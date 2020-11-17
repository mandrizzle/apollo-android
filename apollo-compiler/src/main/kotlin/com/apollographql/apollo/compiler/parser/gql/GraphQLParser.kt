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
  /**
   * Parses and validates the given SDL schema document
   *
   * This voluntarily does not return a GQLDocument in order to explicitly distinguish between operations and schemas
   * throws if the schema has errors or is not a schema file
   */
  fun parseSchema(source: BufferedSource, filePath: String? = null): ParseResult<Schema> {
    return parseDocument(source, filePath)
        .mapValue {
          it.mergeTypeExtensions()
              // Validation as to be done before adding the built in types else validation fail on names starting with '__'
              // This means that it's impossible to add type extensions on built in types at the moment
            .validateAsSchema()
            .withBuiltinTypes()
            .toSchema()
        }
  }

  /**
   * Parses and validates the given SDL schema document
   *
   * This voluntarily does not return a GQLDocument in order to explicitly distinguish between operations and schemas
   * throws if the schema has errors or is not a schema file
   */
  fun parseSchema(string: String) = parseSchema(string.byteInputStream().source().buffer())

  /**
   * Parses and validates the given SDL schema document
   *
   * This voluntarily does not return a GQLDocument in order to explicitly distinguish between operations and schemas
   * throws if the schema has errors or is not a schema file
   */
  fun parseSchema(file: File) = parseSchema(file.source().buffer(), file.absolutePath)

  /**
   * Parses and validates the given SDL executable document, containing operations and/or fragments
   *
   * This voluntarily does not return a GQLDocument in order to explicitly distinguish between operations and schemas
   * throws if the schema has errors or is not a schema file
   */
  fun parseExecutable(source: BufferedSource, filePath: String? = null, schema: Schema): ParseResult<GQLDocument> {
    return antlrParse(source, filePath).mapValue {
      it.toGQLDocument(filePath)
    }.map {
      ParseResult(
          it,
          it.validateAsOperation(schema)
              + it.definitions.checkDuplicates()
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

  /**
   * A specialized version that works on multiple files and can also inject fragments from another
   * compilation unit
   */
  fun parseExecutables(files: Set<File>, schema: Schema, injectedFragmentDefinitions: List<GQLFragmentDefinition>): ParseResult<List<GQLDocument>> {
    val (documents, parsingIssues) = files.map { parseDocument(it) }
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

    val validationIssues = documents.flatMap { document ->
      document.definitions.flatMap { definition ->
        when (definition) {
          is GQLFragmentDefinition -> {
            // This will catch unused fragments that are invalid. It might not be strictly needed
            definition.validate(schema, allFragments)
          }
          is GQLOperationDefinition -> {
            definition.validate(schema, allFragments)
          }
          else -> {
            listOf(
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
        parsingIssues + duplicateIssues + validationIssues
    )
  }

  /**
   * Parses a GraphQL document without doing any kind of validation besides the grammar parsing.
   *
   * Use [parseExecutable] to parse operations and [parseSchema] to have proper validation
   */
  fun parseDocument(source: BufferedSource, filePath: String?): ParseResult<GQLDocument> = source.use { _ ->
    antlrParse(source, filePath).mapValue {
      it.toGQLDocument(filePath)
    }
  }

  fun parseDocument(file: File) = parseDocument(file.source().buffer(), file.absolutePath)

  fun parseDocument(string: String) = parseDocument(string.byteInputStream().source().buffer(), null)

  fun builtinTypes(): GQLDocument {
    val source = GQLDocument::class.java.getResourceAsStream("/builtins.sdl")
        .source()
        .buffer()
    return antlrParse(source).orThrow().toGQLDocument(null)
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