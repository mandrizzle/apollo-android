package com.apollographql.apollo.compiler.parser.graphql.ast

/**
 * a very thin wrapper around a schema GQLDocument
 *
 * It serves as a common ground between GQLDocument and IntrospectionSchema
 *
 * Schema should always contain all types, including the builtin ones
 */
class Schema(
    val typeDefinitions: Map<String, GQLTypeDefinition>,
    val queryTypeDefinition: GQLTypeDefinition,
    val mutationTypeDefinition: GQLTypeDefinition?,
    val subscriptionTypeDefinition: GQLTypeDefinition?,
) {
  fun toDocument(): GQLDocument = GQLDocument(
      definitions = typeDefinitions.values.toList(),
      filePath = null
  ).withoutBuiltinTypes()

  fun typeDefinition(name: String): GQLTypeDefinition {
    return typeDefinitions[name] ?: throw SchemaValidationException("Cannot find type `$name`")
  }
}