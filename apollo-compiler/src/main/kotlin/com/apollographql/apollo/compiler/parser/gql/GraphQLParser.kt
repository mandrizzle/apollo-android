package com.apollographql.apollo.compiler.parser.gql

import com.apollographql.apollo.compiler.parser.antlr.GraphQLLexer
import com.apollographql.apollo.compiler.parser.antlr.GraphQLParser as AntlrGraphQLParser
import okio.BufferedSource
import okio.buffer
import okio.source
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.atn.PredictionMode
import java.io.File

/**
 * Entry point for parsing and validating GraphQL objects
 */
object GraphQLParser {
  fun parse(source: BufferedSource, filePath: String?): ParseResult<GQLDocument> = source.use { _ ->
    antlrParse(source, filePath).mapValue {
      it.toGQLDocument(filePath)
    }
  }

  fun parse(file: File) = parse(file.source().buffer(), file.absolutePath)

  fun parse(string: String) = parse(string.byteInputStream().source().buffer(), null)

  /**
   * Parse the given SDL schema
   *
   * throws if the schema has errors or is not a schema file
   */
  fun parseSchema(source: BufferedSource, filePath: String? = null): Schema {
    return parse(source, filePath)
        .getOrThrow()
        // Validation as to be done before adding the built in types else validation fail on names starting with '__'
        // This means that it's impossible to add type extensions on built in types at the moment
        .mergeTypeExtensions()
        .validateAsSchema()
        .withBuiltinTypes()
        .toSchema()
  }

  /**
   * Parse the given SDL schema
   *
   * throws if the schema has errors or is not a schema file
   */
  fun parseSchema(string: String) = parseSchema(string.byteInputStream().source().buffer())

  /**
   * Parse the given SDL schema
   * 
   * throws if the schema has errors or is not a schema file
   */
  fun parseSchema(file: File) = parseSchema(file.source().buffer(), file.absolutePath)

  fun builtinTypes(): GQLDocument {
    val source = GQLDocument::class.java.getResourceAsStream("/builtins.sdl")
        .source()
        .buffer()
    return antlrParse(source).getOrThrow().toGQLDocument(null)
  }

  /**
   * Analyze the given SDL document
   */
  fun parseExecutable(source: BufferedSource, filePath: String? = null, schema: Schema): ParseResult<GQLDocument> {
    return antlrParse(source, filePath).mapValue {
      it.toGQLDocument(filePath)
    }.map {
      val fragments = it.definitions.filterIsInstance<GQLFragmentDefinition>().associateBy { it.name }
      ParseResult(
          it,
          it.validateAsExecutable(schema, fragments) + it.definitions.checkDuplicates()
      )
    }
  }

  /**
   * Parse the given SDL document
   */
  fun parseExecutable(string: String, schema: Schema) = parseExecutable(string.byteInputStream().source().buffer(), null, schema)

  /**
   * Parse the given SDL document
   */
  fun parseExecutable(file: File, schema: Schema) = file.source().buffer().use {
    parseExecutable(it, file.absolutePath, schema)
  }

  private fun List<GQLFragmentDefinition>.checkDuplicateFragments(): List<Issue> {
    val filtered = mutableMapOf<String, GQLFragmentDefinition>()
    val issues = mutableListOf<Issue>()

    forEach {
      val existing = filtered.putIfAbsent(it.name, it)
      if (existing != null) {
        issues.add(Issue.ValidationError(
            message = "Fragment ${it.name} is already defined",
            sourceLocation = it.sourceLocation,
        ))
      }
    }
    return issues
  }

  private fun List<GQLOperationDefinition>.checkDuplicateOperations(): List<Issue> {
    val filtered = mutableMapOf<String, GQLOperationDefinition>()
    val issues = mutableListOf<Issue>()

    forEach {
      if (it.name == null) {
        issues.add(Issue.ValidationError(
            message = "Apollo does not support anonymous operations",
            sourceLocation = it.sourceLocation,
        ))
        return@forEach
      }
      val existing = filtered.putIfAbsent(it.name, it)
      if (existing != null) {
        issues.add(Issue.ValidationError(
            message = "Operation ${it.name} is already defined",
            sourceLocation = it.sourceLocation,
        ))
      }
    }
    return issues
  }

  private fun List<GQLDefinition>.checkDuplicates(): List<Issue> {
    return filterIsInstance<GQLOperationDefinition>().checkDuplicateOperations() + filterIsInstance<GQLFragmentDefinition>().checkDuplicateFragments()
  }

  /**
   * A specialized version that works on multiple files and can also inject fragments from another
   * compilation unit
   */
  fun parseExecutables(files: Set<File>, schema: Schema, injectedFragmentDefinitions: List<GQLFragmentDefinition>): ParseResult<List<GQLDocument>> {
    val issues = mutableListOf<Issue>()

    val (documents, documentIssues) = files.map { parse(it) }
        .fold(ParseResult<List<GQLDocument>>(emptyList(), emptyList())) { acc, item ->
          ParseResult(
              acc.value + item.value,
              acc.issues + item.issues
          )
        }

    val duplicateIssues = documents.flatMap { it.definitions }.checkDuplicates()

    /**
     * Collect all fragments as operations might use fragments from different files
     */
    val allFragments= (documents.filterIsInstance<GQLFragmentDefinition>() + injectedFragmentDefinitions).associateBy {
      it.name
    }

    documents.forEach { document ->
      document.definitions.forEach { definition ->
        when (definition) {
          is GQLFragmentDefinition -> {
            // This will catch unused fragments that are invalid. It might not be strictly needed
            definition.validate(schema, allFragments)
          }
          is GQLOperationDefinition -> {
            definition.validate(schema, allFragments)
          }
          else -> {
            issues.add(
                Issue.ValidationError(
                    message = "Non-executable definition found",
                    sourceLocation = definition.sourceLocation,
                )
            )
          }
        }
      }
    }

    return ParseResult(
        documents,
        documentIssues + duplicateIssues
    )
  }

  /**
   * Plain parsing, without validation or adding the builtin types
   */
  private fun antlrParse(source: BufferedSource, filePath: String? = null): ParseResult<AntlrGraphQLParser.DocumentContext> {

    val parser = AntlrGraphQLParser(
        CommonTokenStream(
            GraphQLLexer(
                CharStreams.fromStream(source.inputStream())
            )
        )
    )

    val issues = mutableListOf<Issue>()
    return parser.apply {
      removeErrorListeners()
      interpreter.predictionMode = PredictionMode.SLL
      addErrorListener(
          object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>?,
                offendingSymbol: Any?,
                line: Int,
                position: Int,
                msg: String?,
                e: RecognitionException?
            ) {
              issues.add(Issue.ParsingError(
                  message = "Unsupported token `${(offendingSymbol as? Token)?.text ?: offendingSymbol.toString()}`",
                  sourceLocation = SourceLocation(line, position, filePath)
              ))
            }
          }
      )
    }.document()
        .also {
          parser.checkEOF(it, filePath)
        }
        .let {
          ParseResult(it, issues)
        }
  }

  private fun AntlrGraphQLParser.checkEOF(documentContext: AntlrGraphQLParser.DocumentContext, filePath: String?) {
    val documentStopToken = documentContext.getStop()
    val allTokens = (tokenStream as CommonTokenStream).tokens
    if (documentStopToken != null && !allTokens.isNullOrEmpty()) {
      val lastToken = allTokens[allTokens.size - 1]
      val eof = lastToken.type == Token.EOF
      val sameChannel = lastToken.channel == documentStopToken.channel
      if (!eof && lastToken.tokenIndex > documentStopToken.tokenIndex && sameChannel) {
        throw AntlrParseException(
            message = "Unsupported token `${lastToken.text}`",
            sourceLocation = SourceLocation(lastToken.line, lastToken.charPositionInLine, filePath)
        )
      }
    }
  }
}