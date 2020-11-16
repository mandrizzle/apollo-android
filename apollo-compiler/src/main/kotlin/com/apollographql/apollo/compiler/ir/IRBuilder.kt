package com.apollographql.apollo.compiler.ir

import com.apollographql.apollo.compiler.ApolloMetadata
import com.apollographql.apollo.compiler.PackageNameProvider
import com.apollographql.apollo.compiler.parser.graphql.ast.GQLDocument
import com.apollographql.apollo.compiler.parser.graphql.ast.GQLEnumTypeDefinition
import com.apollographql.apollo.compiler.parser.graphql.ast.GQLFragmentDefinition
import com.apollographql.apollo.compiler.parser.graphql.ast.GQLInputObjectTypeDefinition
import com.apollographql.apollo.compiler.parser.graphql.ast.GQLOperationDefinition
import com.apollographql.apollo.compiler.parser.graphql.ast.GQLScalarTypeDefinition
import com.apollographql.apollo.compiler.parser.graphql.ast.Schema
import com.apollographql.apollo.compiler.parser.graphql.ast.isBuiltIn
import com.apollographql.apollo.compiler.parser.graphql.ast.toIR
import com.apollographql.apollo.compiler.parser.graphql.ast.usedTypeNames
import com.apollographql.apollo.compiler.parser.graphql.ast.withTypenameWhenNeeded

class IRBuilder(private val schema: Schema,
                private val schemaPackageName: String,
                private val incomingMetadata: ApolloMetadata?,
                private val alwaysGenerateTypesMatching: Set<String>?,
                generateMetadata: Boolean,
                val packageNameProvider: PackageNameProvider
) {
  private val isRootCompilationUnit = incomingMetadata == null && generateMetadata

  private fun extraTypes(): Set<String> {
    val regexes = (alwaysGenerateTypesMatching ?: (listOf(".*").takeIf { isRootCompilationUnit } ?: emptyList()))
        .map { Regex(it) }

    return schema.typeDefinitions.values.filter { typeDefinition ->
      (typeDefinition is GQLInputObjectTypeDefinition || typeDefinition is GQLEnumTypeDefinition)
          && regexes.indexOfFirst { it.matches(typeDefinition.name) } >= 0
    }.map { it.name }
        .toSet()
  }

  fun build(documents: List<GQLDocument>): CodeGenerationIR {
    val documentFragmentTypeDefinitions = documents.flatMap { it.definitions.filterIsInstance<GQLFragmentDefinition>() }
    val allFragments = ((incomingMetadata?.fragments ?: emptyList()) + documentFragmentTypeDefinitions).map { it.withTypenameWhenNeeded(schema) }

    val fragmentsToGenerate = documentFragmentTypeDefinitions.map { it.name }

    val incomingTypes = incomingMetadata?.types ?: emptySet()
    val extraTypes = extraTypes()

    val typeDeclarations = (documents.flatMap { it.definitions.filterIsInstance<GQLOperationDefinition>() }.usedTypeNames(schema, allFragments.associateBy { it.name }) + extraTypes).map {
      schema.typeDefinitions[it]!!
    }.filter {
      when (it) {
        is GQLEnumTypeDefinition,
        is GQLInputObjectTypeDefinition -> true
        else -> false
      }
    }

    val enumsToGenerate = typeDeclarations.filterIsInstance<GQLEnumTypeDefinition>()
        .map { it.name }
        .filter { !incomingTypes.contains(it) }

    val inputObjectsToGenerate = typeDeclarations.filterIsInstance<GQLInputObjectTypeDefinition>()
        .map { it.name }
        .filter { !incomingTypes.contains(it) }

    val scalarsToGenerate = when {
      // multi-module -> the scalar types will be already generated
      incomingMetadata != null -> emptyList()
      // non multi-module or root -> generate all scalar types
      else -> schema.typeDefinitions
          .values
          .filterIsInstance<GQLScalarTypeDefinition>()
          .filter{ !it.isBuiltIn() }
          .map { it.name } + "ID" // not sure why we need to add ID there
    }

    return CodeGenerationIR(
        operations = documents.flatMap { it.definitions.filterIsInstance<GQLOperationDefinition>() }.map {
          it.withTypenameWhenNeeded(schema).toIR(schema, allFragments.associateBy { it.name }, packageNameProvider)
        },
        fragments = allFragments.map { it.toIR(schema, allFragments.associateBy { it.name }) },
        typeDeclarations = typeDeclarations.map { it.toIR(schema) },
        fragmentsToGenerate = fragmentsToGenerate.toSet(),
        enumsToGenerate = enumsToGenerate.toSet(),
        inputObjectsToGenerate = inputObjectsToGenerate.toSet(),
        scalarsToGenerate = scalarsToGenerate.toSet(),
        typesPackageName = "$schemaPackageName.type".removePrefix("."),
        fragmentsPackageName = "$schemaPackageName.fragment".removePrefix(".")
    )
  }
}