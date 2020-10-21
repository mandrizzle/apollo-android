package com.apollographql.apollo.compiler

import com.apollographql.apollo.compiler.parser.introspection.IntrospectionSchema
import com.apollographql.apollo.compiler.parser.sdl.GraphSDLSchemaParser.parse
import com.apollographql.apollo.compiler.parser.sdl.toIntrospectionSchema
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelNameTest {

  /**
   * a mapping from type conditions to the actual concrete types that satisfy them
   */
  data class Mapping(val typeConditions: Set<String>, val concreteTypes: Set<String>)

  /**
   * a generated model.
   *
   * name is the concatenation of all the Type conditions this model inherits
   * concreteTypes is an explanation of the concrete types that will be implemented by this model
   */
  data class Model(val name: String, val concreteTypes: Set<String>)

  private fun test(sdl: String, typeConditions: Set<String>, expected: Set<Model>) {
    val schema = sdl.parse().toIntrospectionSchema()

    var candidates = typeConditions.subsets().map {
      Mapping(it, schema.possibleTypes(it))
    }.filter {
      // some combination of fragments do not map to any concrete type, forget them
      it.concreteTypes.isNotEmpty()
    }

    val requiredMappings = mutableListOf<Mapping>()
    while (!candidates.isEmpty()) {
      val (required, remaining) = candidates.groupBy { it.concreteTypes }.entries.sortedBy { it.key.size }
          .map { it.value }
          .let {
            it.first() to it.slice(1 until it.size)
          }

      // Take the most qualified mapping and remember it
      val r = required.map { Mapping(it.typeConditions.normalize(schema), it.concreteTypes) }.sortedByDescending { it.typeConditions.size }.first()
      requiredMappings.add(r)

      // Update the candidate and remove that concreteType combination
      candidates = remaining.flatten()
          .map { it.remove(r.concreteTypes) }

    }

    val actual = requiredMappings.map {
      Model(it.typeConditions.sorted().joinToString(""), it.concreteTypes)
    }.toSet()

    assertThat(actual).isEqualTo(expected)
  }

  /**
   * If a list of type conditions defines a concrete type and its interface, we can remove the interface
   */
  private fun Set<String>.normalize(schema: IntrospectionSchema): Set<String> {
    val concreteTypes = filter { schema.types[it] is IntrospectionSchema.Type.Object }
    check(concreteTypes.size <= 1) {
      "shouldn't happen"
    }

    if (concreteTypes.isEmpty()) {
      // only unions on interface
      return this
    }
    val interfaces = schema.types.values.filterIsInstance(IntrospectionSchema.Type.Interface::class.java)
        .filter { it.possibleTypes!!.map { it.name }.contains(concreteTypes.first()) }
        .map { it.name }

    return this.subtract(interfaces.toSet())
  }

  @Test
  fun test1() {
    test(
      sdl = """
      interface Character {}
      interface Being {}
      interface Thing {}
      
      type Droid implements Character & Thing {}
      type Human implements Character & Being {}
      type Starship implements Thing {}
      type Wookie implements Character & Being {}
      type Jedi implements Being {}      
    """.trimIndent(),
      typeConditions = setOf("Character"),
      expected = setOf(
        Model("Character", setOf("Droid", "Human", "Wookie"))
      )
    )
  }

  @Test
  fun test2() {
    test(
      sdl = """
      interface Character {}
      interface Being {}
      interface Thing {}
      
      type Droid implements Character & Thing {}
      type Human implements Character & Being {}
      type Starship implements Thing {}
      type Wookie implements Character & Being {}
      type Jedi implements Being {}      
    """.trimIndent(),
      typeConditions = setOf("Character", "Being", "Thing"),
      expected = setOf(
        Model("Thing", setOf("Starship")),
        Model("Being", setOf("Jedi")),
        Model("BeingCharacter", setOf("Human", "Wookie")),
        Model("CharacterThing", setOf("Droid"))
      )
    )
  }

  @Test
  fun test3() {
    test(
      sdl = """
      interface Character {}
      interface Being {}
      interface Thing {}
      
      type Droid implements Character & Thing {}
      type Human implements Character & Being {}
      type Starship implements Thing {}
    """.trimIndent(),
      typeConditions = setOf("Character", "Being", "Thing"),
      expected = setOf(
        Model("Thing", setOf("Starship")),
        Model("BeingCharacter", setOf("Human")),
        Model("CharacterThing", setOf("Droid"))
      )
    )
  }

  @Test
  fun test4() {
    test(
      sdl = """
      interface Character {}
      interface Being {}
      interface Thing {}
      
      type Droid implements Character & Thing {}
      type Human implements Character & Being {}
      type Starship implements Thing {}
    """.trimIndent(),
      typeConditions = setOf("Droid"),
      expected = setOf(
        Model("Droid", setOf("Droid"))
      )
    )
  }

  @Test
  fun test5() {
    test(
      sdl = """
      interface Character {}
      interface Being {}
      interface Thing {}
      
      type Droid implements Character & Thing {}
      type Human implements Character & Being {}
      type Starship implements Thing {}
    """.trimIndent(),
      typeConditions = setOf("Human", "Droid"),
      expected = setOf(
        Model("Droid", setOf("Droid")),
        Model("Human", setOf("Human"))
      )
    )
  }

  @Test
  fun test6() {
    test(
      sdl = """
      interface Character {}
      interface Being {}
      interface Thing {}
      
      type Droid implements Character & Thing {}
      type Human implements Character & Being {}
    """.trimIndent(),
      typeConditions = setOf("Character", "Droid"),
      expected = setOf(
        Model("Droid", setOf("Droid")),
        Model("Character", setOf("Human")),
      )
    )
  }

  private fun Mapping.remove(concreteTypes: Set<String>) = Mapping(typeConditions, this.concreteTypes.subtract(concreteTypes))

  /**
   * All the concrete types that satisfy the all the type conditions passed
   */
  private fun IntrospectionSchema.possibleTypes(namedTypes: Set<String>): Set<String> {
    return namedTypes.map { possibleTypes(it) }.fold(emptySet()) { acc, set ->
      if (acc.isEmpty()) {
        set
      } else {
        acc.intersect(set)
      }
    }
  }

  private fun IntrospectionSchema.possibleTypes(namedType: String): Set<String> {
    return when (val type = types[namedType]) {
      is IntrospectionSchema.Type.Object -> listOf(type.name)
      is IntrospectionSchema.Type.Interface -> type.possibleTypes?.map { it.name!! }
      is IntrospectionSchema.Type.Union -> type.possibleTypes?.map { it.name!! }
      else -> throw IllegalStateException("cannot implement type $namedType")
    }?.toSet() ?: emptySet()
  }

  private fun Set<String>.subsets(): Set<Set<String>> {
    val asList = toList()
    return (1 until 1.shl(size)).map { i ->
      (0 until size).mapNotNull { j ->
        if (i.and(1.shl(j)) != 0) {
          asList[j]
        } else {
          null
        }
      }.toSet()
    }.toSet()
  }
}



