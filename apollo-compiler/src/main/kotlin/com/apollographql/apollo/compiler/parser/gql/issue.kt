package com.apollographql.apollo.compiler.parser.gql

data class ParseResult<out T>(
    val value: T,
    val issues: List<Issue>
) {
  fun orThrow(): T {
    val firstError = issues.firstOrNull { it.severity == Issue.Severity.ERROR }
    if (firstError != null) {
      throw SourceAwareException(firstError.message, firstError.sourceLocation)
    }
    return value
  }

  fun <R> map(transform: (T) -> ParseResult<R>): ParseResult<R> {
    val result = transform(value)
    return ParseResult(
        result.value,
        issues + result.issues
    )
  }

  fun <R> mapValue(transform: (T) -> R): ParseResult<R> {
    return ParseResult(
        transform(value),
        issues
    )
  }
}

/**
 * All the issues that can be collected while analyzing a graphql document
 */
sealed class Issue(
    val message: String,
    val sourceLocation: SourceLocation,
    val severity: Severity
) {
  class ValidationError(message: String, sourceLocation: SourceLocation) : Issue(message, sourceLocation, Severity.ERROR)
  class UnkownError(message: String, sourceLocation: SourceLocation) : Issue(message, sourceLocation, Severity.ERROR)
  class ParsingError(message: String, sourceLocation: SourceLocation) : Issue(message, sourceLocation, Severity.ERROR)
  class DeprecatedUsage(message: String, sourceLocation: SourceLocation) : Issue(message, sourceLocation, Severity.WARNING)

  enum class Severity {
    WARNING,
    ERROR
  }
}
