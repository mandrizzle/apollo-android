package com.apollographql.apollo.compiler

import com.apollographql.apollo.compiler.TestUtils.checkTestFixture
import com.apollographql.apollo.compiler.parser.gql.GraphQLParser
import com.apollographql.apollo.compiler.parser.gql.toFile
import com.apollographql.apollo.compiler.parser.gql.toIntrospectionSchema
import com.apollographql.apollo.compiler.parser.gql.toSchema
import com.apollographql.apollo.compiler.parser.introspection.IntrospectionSchema
import com.apollographql.apollo.compiler.parser.introspection.IntrospectionSchema.Companion.wrap
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class GraphSdlParseTest() {

  @Test
  fun `SDL schema parsed successfully and produced the same introspection schema`() {
    /**
     * Things to watch out:
     * - leading/trailing spaces in descriptions
     * - defaultValue coercion
     */
    val schema = GraphQLParser.parseSchema(File("src/test/sdl/schema.sdl"))
    val actualSchema = schema.toIntrospectionSchema().normalize()
    val expectedSchemaFile = File("src/test/sdl/schema.json")
    val actualSchemaFile = File("build/sdl-test/actual.json")

    actualSchemaFile.parentFile.mkdirs()
    actualSchema.wrap().toJson(actualSchemaFile, "  ")

    checkTestFixture(actual = actualSchemaFile, expected = expectedSchemaFile)
  }

  private fun IntrospectionSchema.normalize(): IntrospectionSchema {
    return copy(types = toSortedMap().mapValues { (_, type) -> type.normalize() })
  }

  /**
   * GraphQL has Int and Float, json has only Number, map everything to Double
   */
  private fun Any?.normalizeNumbers(): Any? {
    return when (this) {
      is List<*> -> this.map { it?.normalizeNumbers() }
      is Number -> this.toDouble()
      else -> this
    }
  }

  private fun IntrospectionSchema.Type.normalize(): IntrospectionSchema.Type {
    return when (this) {
      is IntrospectionSchema.Type.Scalar -> this
      is IntrospectionSchema.Type.Object -> copy(fields = fields?.sortedBy { field -> field.name })
      is IntrospectionSchema.Type.Interface -> copy(fields = fields?.sortedBy { field -> field.name })
      is IntrospectionSchema.Type.Union -> copy(fields = fields?.sortedBy { field -> field.name })
      is IntrospectionSchema.Type.Enum -> this
      is IntrospectionSchema.Type.InputObject -> copy(inputFields = inputFields.map {
        it.copy(defaultValue = it.defaultValue.normalizeNumbers())
      }.sortedBy { field -> field.name })
    }
  }


  @Test
  fun `writing SDL and parsing again yields identical schemas`() {
    val initialSchema = IntrospectionSchema(File("src/test/sdl/schema.json")).normalize()
    val sdlFile = File("build/sdl-test/schema.sdl")
    sdlFile.parentFile.deleteRecursively()
    sdlFile.parentFile.mkdirs()
    initialSchema.toSchema().toDocument().toFile(sdlFile)
    val finalSchema = GraphQLParser.parseSchema(sdlFile).toIntrospectionSchema().normalize()

    dumpSchemas(initialSchema, finalSchema)
    assertEquals(initialSchema, finalSchema)
  }

  /**
   * use to make easier diffs
   * Use meld or any other outside tool to compare
   */
  private fun dumpSchemas(expected: IntrospectionSchema, actual: IntrospectionSchema) {
    File("build/sdl-test").mkdirs()
    actual.wrap().toJson(File("build/sdl-test/actual.json"), "  ")
    expected.wrap().toJson(File("build/sdl-test/expected.json"), "  ")
  }
}
