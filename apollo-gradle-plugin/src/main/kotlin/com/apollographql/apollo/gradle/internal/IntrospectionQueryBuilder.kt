package com.apollographql.apollo.gradle.internal

object IntrospectionQueryBuilder {
  private fun build(queryPrefix: String,
                    querySuffix: String,
                    typesPrefix: String,
                    includeDeprecatedArguments: String
  ) = """
    $queryPrefix
        queryType { name }
        mutationType { name }
        subscriptionType { name }
        types {
          ...FullType
        }
        directives {
          name
          description
          locations
          args {
            ...InputValue
          }
        }
    $querySuffix

    fragment FullType on ${typesPrefix}Type {
      kind
      name
      description
      fields$includeDeprecatedArguments {
        name
        description
        args {
          ...InputValue
        }
        type {
          ...TypeRef
        }
        isDeprecated
        deprecationReason
      }
      inputFields {
        ...InputValue
      }
      interfaces {
        ...TypeRef
      }
      enumValues$includeDeprecatedArguments {
        name
        description
        isDeprecated
        deprecationReason
      }
      possibleTypes {
        ...TypeRef
      }
    }

    fragment InputValue on ${typesPrefix}InputValue {
      name
      description
      type { ...TypeRef }
      defaultValue
    }

    fragment TypeRef on ${typesPrefix}Type {
      kind
      name
      ofType {
        kind
        name
        ofType {
          kind
          name
          ofType {
            kind
            name
            ofType {
              kind
              name
              ofType {
                kind
                name
                ofType {
                  kind
                  name
                  ofType {
                    kind
                    name
                  }
                }
              }
            }
          }
        }
      }
    }""".trimIndent()


  fun buildForEndpoint() = build(
      queryPrefix = """
        query IntrospectionQuery {
          __schema {
      """.trimIndent(),
      querySuffix = """
          }
        }
      """.trimIndent(),
      typesPrefix = "__",
      includeDeprecatedArguments = "(includeDeprecated: true)"
  )

  fun buildForStudio(service: String, tag: String) = build(
      queryPrefix = """
        query IntrospectionQuery {
          service(id: "$service") {
            schema(tag: "$tag") {
              __schema: introspection {
              """.trimIndent(),
      querySuffix = """ 
              }
            }
          }
        }
      """.trimIndent(),
      typesPrefix = "Introspection",
      includeDeprecatedArguments = ""
  )
}