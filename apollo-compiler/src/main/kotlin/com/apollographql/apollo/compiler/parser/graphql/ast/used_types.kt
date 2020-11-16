package com.apollographql.apollo.compiler.parser.graphql.ast

/**
 * XXX: optimize to not visit the same fragment multiple times
 */
private class UsedTypeNamesCollector(val schema: Schema, val fragmentDefinitions: Map<String, GQLFragmentDefinition>) {
  val typeDefinitions = schema.typeDefinitions
  val visitedInputTypes = mutableSetOf<String>()

  fun operationUsedTypeNames(operations: List<GQLOperationDefinition>): Set<String> {
    val inputTypeNames = operations.flatMap { it.variableDefinitions.flatMap { it.usedTypeNames() } }

    val visitedInputTypes = mutableSetOf<String>()
    val typeNames = inputTypeNames.toMutableSet()
    inputTypeNames.forEach {
      (typeDefinitions[it] as? GQLInputObjectTypeDefinition)?.findUsedTypeNames(visitedInputTypes, typeNames)
    }

    typeNames.addAll(operations.flatMap { it.selectionSet.usedTypeNames(it.rootTypeDefinition(schema)!!) })

    return typeNames.toSet()
  }

  private fun GQLInputObjectTypeDefinition.findUsedTypeNames(visitedInputTypes: MutableSet<String>, typeNames: MutableSet<String>){
    if (visitedInputTypes.contains(name)) {
      return
    }
    visitedInputTypes.add(name)

    typeNames.add(name)

    inputFields.forEach {
      when(val inputFieldTypeDefinition = typeDefinitions[it.type.leafType().name]!!) {
        is GQLInputObjectTypeDefinition -> inputFieldTypeDefinition.findUsedTypeNames(visitedInputTypes, typeNames)
        else -> typeNames.add(name)
      }
    }
  }

  private fun GQLOperationDefinition.usedTypeNames(): Set<String> {
    return selectionSet.usedTypeNames(rootTypeDefinition(schema)!!) + variableDefinitions.flatMap { it.usedTypeNames() }

  }
  private fun GQLVariableDefinition.usedTypeNames(): Set<String> {
    return setOf(type.leafType().name)
  }

  private fun GQLSelectionSet.usedTypeNames(typeDefinitionInScope: GQLTypeDefinition): Set<String> {
    return selections.flatMap {
      when (it) {
        is GQLField -> it.usedTypeNames(typeDefinitionInScope)
        is GQLInlineFragment -> it.usedTypeNames()
        is GQLFragmentSpread -> it.usedTypeNames()
      }
    }.toSet()
  }

  private fun GQLField.usedTypeNames(typeDefinitionInScope: GQLTypeDefinition): Set<String> {
    val fieldTypeName = definitionFromScope(schema, typeDefinitionInScope)!!.type.leafType().name
    val fieldTypeDefinition = typeDefinitions[fieldTypeName]!!

    return setOf(fieldTypeName) + (selectionSet?.usedTypeNames(fieldTypeDefinition) ?: emptySet())
  }

  private fun GQLInlineFragment.usedTypeNames() = selectionSet.usedTypeNames(typeDefinitions[typeCondition.name]!!)

  private fun GQLFragmentSpread.usedTypeNames() = fragmentDefinitions[name]!!.let{
    it.selectionSet.usedTypeNames(typeDefinitions[it.typeCondition.name]!!)
  }
}


/**
 * Return all fields and variable type names
 */
fun List<GQLOperationDefinition>.usedTypeNames(schema: Schema, fragmentDefinitions: Map<String, GQLFragmentDefinition>) = UsedTypeNamesCollector(schema, fragmentDefinitions).operationUsedTypeNames(this)

