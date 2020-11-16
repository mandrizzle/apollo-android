package com.apollographql.apollo.compiler

import com.apollographql.apollo.api.internal.QueryDocumentMinifier
import com.apollographql.apollo.compiler.ApolloMetadata.Companion.merge
import com.apollographql.apollo.compiler.codegen.GraphQLKompiler
import com.apollographql.apollo.compiler.ir.Field
import com.apollographql.apollo.compiler.ir.IRBuilder
import com.apollographql.apollo.compiler.ir.ScalarType
import com.apollographql.apollo.compiler.ir.SourceLocation
import com.apollographql.apollo.compiler.operationoutput.OperationDescriptor
import com.apollographql.apollo.compiler.operationoutput.toJson
import com.apollographql.apollo.compiler.parser.error.DocumentParseException
import com.apollographql.apollo.compiler.parser.error.ParseException
import com.apollographql.apollo.compiler.parser.graphql.DocumentParseResult
import com.apollographql.apollo.compiler.parser.graphql.ast.GQLFragmentDefinition
import com.apollographql.apollo.compiler.parser.graphql.ast.GraphQLParser
import com.apollographql.apollo.compiler.parser.graphql.ast.Issue
import com.apollographql.apollo.compiler.parser.graphql.ast.Schema
import com.apollographql.apollo.compiler.parser.graphql.ast.SourceAwareException
import com.apollographql.apollo.compiler.parser.graphql.ast.toIntrospectionSchema
import com.apollographql.apollo.compiler.parser.graphql.ast.toSchema
import com.apollographql.apollo.compiler.parser.introspection.IntrospectionSchema
import com.squareup.kotlinpoet.asClassName
import java.io.File

class GraphQLCompiler(val logger: Logger = NoOpLogger) {

  interface Logger {
    fun warning(message: String)
  }

  fun write(args: Arguments) {
    args.outputDir.deleteRecursively()
    args.outputDir.mkdirs()

    val roots = Roots(args.rootFolders)
    val metadata = collectMetadata(args.metadata)

    val (schema, schemaPackageName) = getSchemaInfo(roots, args.rootPackageName, args.schemaFile, metadata)

    val generateKotlinModels = metadata?.generateKotlinModels ?: args.generateKotlinModels
    val userCustomTypesMap = metadata?.customTypesMap ?: args.customTypeMap

    val packageNameProvider = DefaultPackageNameProvider(
        roots = roots,
        rootPackageName = args.rootPackageName
    )

    val (documents, issues) = GraphQLParser.parseExecutables(
        args.graphqlFiles,
        schema,
        metadata?.fragments ?: emptyList()
    )

    val (errors, warnings) = issues.partition { it.severity == Issue.Severity.ERROR }

    val firstError = errors.firstOrNull()
    if (firstError != null) {
      throw SourceAwareException(
          message = firstError.message,
          sourceLocation = firstError.sourceLocation,
      )
    }

    if (args.warnOnDeprecatedUsages) {
      warnings.forEach {
        // antlr is 0-indexed but IntelliJ is 1-indexed. Add 1 so that clicking the link will land on the correct location
        val column = it.sourceLocation.position + 1
        // Using this format, IntelliJ will parse the warning and display it in the 'run' panel
        // XXX: uniformize with error handling above
        logger.warning("w: ${it.sourceLocation.filePath}:${it.sourceLocation.line}:${column}: ApolloGraphQL: ${it.message}")
      }
      if (args.failOnWarnings && warnings.isNotEmpty()) {
        throw IllegalStateException("ApolloGraphQL: Warnings found and 'failOnWarnings' is true, aborting.")
      }
    }

    val ir = IRBuilder(
        schema = schema,
        schemaPackageName = schemaPackageName,
        incomingMetadata = metadata,
        alwaysGenerateTypesMatching = args.alwaysGenerateTypesMatching,
        generateMetadata = args.generateMetadata,
        packageNameProvider = packageNameProvider
    ).build(documents)

    if (args.dumpIR) {
      ir.toJson(File(args.outputDir, "ir.json"))
    }

    val operationOutput = ir.operations.map {
      OperationDescriptor(
          name = it.operationName,
          packageName = it.packageName,
          filePath = it.filePath,
          source = QueryDocumentMinifier.minify(it.sourceWithFragments)
      )
    }.let {
      args.operationOutputGenerator.generate(it)
    }

    check(operationOutput.size == ir.operations.size) {
      """The number of operation IDs (${operationOutput.size}) should match the number of operations (${ir.operations.size}).
        |Check that all your IDs are unique.
      """.trimMargin()
    }

    if (args.operationOutputFile != null) {
      args.operationOutputFile.writeText(operationOutput.toJson("  "))
    }

    // TODO: use another schema for codegen than introspection schema
    val introspectionSchema = schema.toIntrospectionSchema()
    val customTypeMap = (introspectionSchema.types.values.filter {
      it is IntrospectionSchema.Type.Scalar && ScalarType.forName(it.name) == null
    }.map { it.name } + ScalarType.ID.name)
        .supportedTypeMap(userCustomTypesMap, generateKotlinModels)

    GraphQLKompiler(
        ir = ir,
        schema = introspectionSchema,
        customTypeMap = customTypeMap,
        operationOutput = operationOutput,
        useSemanticNaming = args.useSemanticNaming,
        generateAsInternal = args.generateAsInternal,
        generateFilterNotNull = args.generateFilterNotNull,
        enumAsSealedClassPatternFilters = args.enumAsSealedClassPatternFilters.map { it.toRegex() }
    ).write(args.outputDir)

    args.metadataOutputFile.parentFile.mkdirs()
    if (args.generateMetadata) {
      val outgoingMetadata = ApolloMetadata(
          schema = if (metadata == null) schema else null,
          schemaPackageName = schemaPackageName,
          moduleName = args.moduleName,
          types = ir.enumsToGenerate + ir.inputObjectsToGenerate,
          fragments = documents.flatMap { it.definitions.filterIsInstance<GQLFragmentDefinition>() },
          generateKotlinModels = generateKotlinModels,
          customTypesMap = args.customTypeMap,
          pluginVersion = VERSION
      )
      outgoingMetadata.writeTo(args.metadataOutputFile)
    } else {
      // write a dummy metadata because the file is required as part as the `assemble` target
      args.metadataOutputFile.writeText("")
    }
  }

  private class DeprecatedUsage(val filePath: String, val sourceLocation: SourceLocation, val field: Field)

  private fun DocumentParseResult.collectDeprecatedUsages(): List<DeprecatedUsage> {
    return operations.flatMap { it.fields.collectDeprecatedUsages(it.filePath) } +
        fragments.flatMap { it.fields.collectDeprecatedUsages(it.filePath) }
  }

  /**
   * walk the list and return any deprecated fields
   * TODO: add support for deprecated enums
   */
  private fun List<Field>.collectDeprecatedUsages(filePath: String): List<DeprecatedUsage> {
    val fieldsToVisit = mutableListOf<Field>()
    val deprecatedUsages = mutableListOf<DeprecatedUsage>()
    fieldsToVisit.addAll(this)
    while (fieldsToVisit.isNotEmpty()) {
      val field = fieldsToVisit.removeAt(fieldsToVisit.lastIndex)
      if (field.isDeprecated) {
        deprecatedUsages.add(DeprecatedUsage(filePath, field.sourceLocation, field))
      }
      fieldsToVisit.addAll(field.fields)
    }
    return deprecatedUsages
  }

  private fun idClassName(generateKotlinModels: Boolean) = if (generateKotlinModels) {
    String::class.asClassName().toString()
  } else {
    TODO("ClassNames.STRING.toString()")
  }

  private fun anyClassName(generateKotlinModels: Boolean) = if (generateKotlinModels) {
    Any::class.asClassName().toString()
  } else {
    TODO("ClassNames.OBJECT.toString()")
  }

  private fun List<String>.supportedTypeMap(customTypeMap: Map<String, String>, generateKotlinModels: Boolean): Map<String, String> {
    return associate {
      val userClassName = customTypeMap[it]
      val className = when {
        userClassName != null -> userClassName
        // map ID to String by default
        it == ScalarType.ID.name -> idClassName(generateKotlinModels)
        // unknown scalars will be mapped to Object/Any
        else -> anyClassName(generateKotlinModels)
      }

      it to className
    }
  }

  companion object {

    private fun collectMetadata(metadata: List<File>): ApolloMetadata? {
      return metadata.mapNotNull {
        ApolloMetadata.readFrom(it)
      }.merge()
    }

    private data class SchemaInfo(val schema: Schema, val schemaPackageName: String)

    private fun getSchemaInfo(roots: Roots, rootPackageName: String, schemaFile: File?, metadata: ApolloMetadata?): SchemaInfo {
      check(schemaFile != null || metadata != null) {
        "ApolloGraphQL: cannot find schema.[json | sdl]"
      }
      check(schemaFile == null || metadata == null) {
        "ApolloGraphQL: You can't define a schema in ${schemaFile?.absolutePath} as one is already defined in a dependency. " +
            "Either remove the schema or the dependency"
      }

      if (schemaFile != null) {
        val schema = if (schemaFile.extension == "json") {
          IntrospectionSchema(schemaFile).toSchema()
        } else {
          try {
            GraphQLParser.parseSchema(schemaFile)
          } catch (e: ParseException) {
            throw DocumentParseException(e, schemaFile.absolutePath)
          }
        }

        val packageName = try {
          roots.filePackageName(schemaFile.absolutePath)
        } catch (e: IllegalArgumentException) {
          // Can happen if the schema is not a child of roots
          ""
        }
        val schemaPackageName = "$rootPackageName.$packageName".removePrefix(".").removeSuffix(".")
        return SchemaInfo(schema, schemaPackageName)
      } else if (metadata != null) {
        return SchemaInfo(metadata.schema!!, metadata.schemaPackageName!!)
      } else {
        throw IllegalStateException("There should at least be metadata or schemaFile")
      }
    }

    val NoOpLogger = object: Logger {
      override fun warning(message: String) {
      }
    }
  }

  /**
   * For more details about the fields defined here, check the gradle plugin
   */
  data class Arguments(
      /**
       * The rootFolders where the graphqlFiles are located. The package name of each individual graphql query
       * will be the relative path to the root folders
       */
      val rootFolders: List<File>,
      /**
       * The files where the graphql queries/fragments are located
       */
      val graphqlFiles: Set<File>,
      /**
       * The schema. Can be either a SDL schema or an introspection schema.
       * If null, the schema, metedata must not be empty
       */
      val schemaFile: File?,
      /**
       * The folder where to generate the sources
       */
      val outputDir: File,

      //========== multi-module ============

      /**
       * A list of files containing metadata from previous compilations
       */
      val metadata: List<File> = emptyList(),
      /**
       * The moduleName for this metadata. Used for debugging purposes
       */
      val moduleName: String = "?",
      /**
       * The file where to write the metadata
       */
      val metadataOutputFile: File,
      val generateMetadata: Boolean = false,
      /**
       * Additional types to generate. This will generate this type and all types this type depends on.
       */
      val alwaysGenerateTypesMatching: Set<String>? = null,

      //========== operation-output ============

      /**
       * the file where to write the operationOutput
       * if null, no operationOutput is written
       */
      val operationOutputFile: File? = null,
      /**
       * the OperationOutputGenerator used to generate operation Ids
       */
      val operationOutputGenerator: OperationOutputGenerator = OperationOutputGenerator.DefaultOperationOuputGenerator(OperationIdGenerator.Sha256()),
      val dumpIR: Boolean = false,

      //========== global codegen options ============

      val rootPackageName: String = "",
      val generateKotlinModels: Boolean = false,
      val customTypeMap: Map<String, String> = emptyMap(),
      val useSemanticNaming: Boolean = true,
      val warnOnDeprecatedUsages: Boolean = true,
      val failOnWarnings: Boolean = false,

      //========== Kotlin codegen options ============

      val generateAsInternal: Boolean = false,
      /**
       * Kotlin native will generate [Any?] for optional types
       * Setting generateFilterNotNull will generate extra `filterNotNull` functions that will help keep the type information
       */
      val generateFilterNotNull: Boolean = false,
      val enumAsSealedClassPatternFilters: Set<String> = emptySet(),
  )
}
