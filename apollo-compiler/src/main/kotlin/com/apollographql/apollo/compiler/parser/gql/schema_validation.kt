package com.apollographql.apollo.compiler.parser.gql

fun GQLDocument.validateAsSchema(): GQLDocument {
  validateNotExecutable()
  validateUniqueSchemaDefinition()
  validateTypeNames()
  validateDirectiveNames()
  validateInterfaces()
  validateObjects()
  return this
}

fun GQLDocument.mergeTypeExtensions(): GQLDocument {
  val (extensions, otherDefinitions) = definitions.partition { it is GQLTypeSystemExtension }
  extensions as List<GQLTypeSystemExtension>

  return copy(
      definitions = extensions.fold(otherDefinitions) { acc, extension ->
        when (extension) {
          is GQLSchemaExtension -> acc.mergeSchemaExtension(schemaExtension = extension)
          is GQLScalarTypeExtension -> acc.merge<GQLScalarTypeDefinition, GQLScalarTypeExtension>(extension) { it.merge(extension) }
          is GQLInterfaceTypeExtension -> acc.merge<GQLInterfaceTypeDefinition, GQLInterfaceTypeExtension>(extension) { it.merge(extension) }
          is GQLObjectTypeExtension -> acc.merge<GQLObjectTypeDefinition, GQLObjectTypeExtension>(extension) { it.merge(extension) }
          is GQLInputObjectTypeExtension -> acc.merge<GQLInputObjectTypeDefinition, GQLInputObjectTypeExtension>(extension) { it.merge(extension) }
          is GQLEnumTypeExtension -> acc.merge<GQLEnumTypeDefinition, GQLEnumTypeExtension>(extension) { it.merge(extension) }
          is GQLUnionTypeExtension -> acc.merge<GQLUnionTypeDefinition, GQLUnionTypeExtension>(extension) { it.merge(extension) }
          else -> throw UnrecognizedAntlrRule("Unrecognized type system extension", extension.sourceLocation)
        }
      }
  )
}

private fun GQLDocument.validateInterfaces() {
  definitions.filterIsInstance<GQLInterfaceTypeDefinition>().forEach {
    if (it.fields.isEmpty()) {
      throw SchemaValidationException("Interfaces must specify one or more fields", it.sourceLocation)
    }
  }
}

private fun GQLDocument.validateObjects() {
  definitions.filterIsInstance<GQLObjectTypeDefinition>().forEach {o ->
    if (o.fields.isEmpty()) {
      throw SchemaValidationException("Object must specify one or more fields", o.sourceLocation)
    }

    o.implementsInterfaces.forEach { implementsInterface ->
      val iface = definitions.firstOrNull { (it as? GQLInterfaceTypeDefinition)?.name == implementsInterface }
      if (iface == null) {
        throw SchemaValidationException("Object '${o.name}' cannot implement non-interface '$implementsInterface'", o.sourceLocation)
      }
    }
  }
}

private fun GQLUnionTypeDefinition.merge(extension: GQLUnionTypeExtension): GQLUnionTypeDefinition {
  return copy(
      directives = directives.mergeUniquesOrThrow(extension.directives),
      memberTypes = memberTypes.mergeUniquesOrThrow(extension.memberTypes)
  )
}

private fun GQLEnumTypeDefinition.merge(extension: GQLEnumTypeExtension): GQLEnumTypeDefinition {
  return copy(
      directives = directives.mergeUniquesOrThrow(extension.directives),
      enumValues = enumValues.mergeUniquesOrThrow(extension.enumValues),
  )
}

private fun GQLInputObjectTypeDefinition.merge(extension: GQLInputObjectTypeExtension): GQLInputObjectTypeDefinition {
  return copy(
      directives = directives.mergeUniquesOrThrow(extension.directives),
      inputFields = inputFields.mergeUniquesOrThrow(extension.inputFields)
  )
}

private fun GQLObjectTypeDefinition.merge(extension: GQLObjectTypeExtension): GQLObjectTypeDefinition {
  return copy(
      directives = directives.mergeUniquesOrThrow(extension.directives),
      fields = fields.mergeUniquesOrThrow(extension.fields),
  )
}

private fun GQLInterfaceTypeDefinition.merge(extension: GQLInterfaceTypeExtension): GQLInterfaceTypeDefinition {
  return copy(
      fields = fields.mergeUniquesOrThrow(extension.fields)
  )
}

private fun List<GQLDefinition>.mergeSchemaExtension(schemaExtension: GQLSchemaExtension): List<GQLDefinition> {
  var found = false
  val definitions = mutableListOf<GQLDefinition>()
  forEach {
    if (it is GQLSchemaDefinition) {
      definitions.add(it.merge(schemaExtension))
      found = true
    } else {
      definitions.add(it)
    }
  }
  if (!found) {
    throw SchemaValidationException("Cannot apply schema extension on non existing schema definition", schemaExtension.sourceLocation)
  }
  return definitions
}

private fun GQLScalarTypeDefinition.merge(scalarTypeExtension: GQLScalarTypeExtension): GQLScalarTypeDefinition {
  return copy(
      directives = directives.mergeUniquesOrThrow(scalarTypeExtension.directives)
  )
}

private inline fun <reified T, E> List<GQLDefinition>.merge(extension: E, merge: (T) -> T): List<GQLDefinition> where T : GQLDefinition, T : GQLNamed, E : GQLNamed, E : GQLNode {
  var found = false
  val definitions = mutableListOf<GQLDefinition>()
  forEach {
    if (it is T && it.name == extension.name) {
      if (found) {
        throw SchemaValidationException("Multiple '${extension.name}' types found while merging extensions. This is a bug, check validation code", extension.sourceLocation)
      }
      definitions.add(merge(it))
      found = true
    } else {
      definitions.add(it)
    }
  }
  if (!found) {
    throw SchemaValidationException("Cannot find type named '${extension.name}' to apply extension", extension.sourceLocation)
  }
  return definitions
}

private fun GQLSchemaDefinition.merge(extension: GQLSchemaExtension): GQLSchemaDefinition {
  return copy(
      directives = directives.mergeUniquesOrThrow(extension.directives),
      rootOperationTypeDefinitions = rootOperationTypeDefinitions.mergeUniquesOrThrow(extension.operationTypesDefinition) { it.operationType }
  )
}

private inline fun <reified T> List<T>.mergeUniquesOrThrow(others: List<T>): List<T> where T : GQLNamed, T : GQLNode {
  return (this + others).apply {
    groupBy { it.name }.entries.firstOrNull { it.value.size > 1 }?.let {
      throw SchemaValidationException("Cannot merge already existing node ${T::class.java.simpleName} `${it.key}`", it.value.first().sourceLocation)
    }
  }
}


private inline fun <reified T : GQLNode> List<T>.mergeUniquesOrThrow(others: List<T>, name: (T) -> String): List<T> {
  return (this + others).apply {
    groupBy { name(it) }.entries.firstOrNull { it.value.size > 1 }?.let {
      throw SchemaValidationException("Cannot merge already existing node ${T::class.java.simpleName} `${it.key}`", it.value.first().sourceLocation)
    }
  }
}

private fun GQLDocument.validateUniqueSchemaDefinition() {
  val schemaDefinitions = definitions.filter { it is GQLSchemaDefinition }
  if (schemaDefinitions.count() > 1) {
    throw SchemaValidationException("multiple schema definitions found", schemaDefinitions.last().sourceLocation)
  }
}


private fun GQLDocument.validateTypeNames() {
  val typeDefinitions = mutableMapOf<String, GQLTypeDefinition>()
  val conflicts = mutableListOf<GQLTypeDefinition>()
  definitions.filterIsInstance<GQLTypeDefinition>().forEach {
    val name = it.name

    if (!typeDefinitions.containsKey(name)) {
      typeDefinitions[name] = it
    } else {
      conflicts.add(it)
    }
  }

  // 3.3 All types within a GraphQL schema must have unique names
  if (conflicts.size > 0) {
    val conflict = conflicts.first()
    throw SchemaValidationException("type '${conflict.name}' is defined multiple times", conflict.sourceLocation)
  }

  // 3.3 All types and directives defined within a schema must not have a name which begins with "__"
  typeDefinitions.forEach { name, definition ->
    if (name.startsWith("__")) {
      throw SchemaValidationException("names starting with '__' are reserved for introspection", definition.sourceLocation)
    }
  }
}

private fun GQLDocument.validateDirectiveNames() {
  val directiveDefinitions = mutableMapOf<String, GQLDirectiveDefinition>()
  val conflicts = mutableListOf<GQLDirectiveDefinition>()
  definitions
      .filterIsInstance<GQLDirectiveDefinition>()
      .forEach {
    val name = it.name

    if (!directiveDefinitions.containsKey(name)) {
      directiveDefinitions.put(name, it)
    } else {
      conflicts.add(it)
    }
  }

  // 3.3 All directives within a GraphQL schema must have unique names.
  if (conflicts.size > 0) {
    val conflict = conflicts.first()
    throw SchemaValidationException("directive '${conflict.name}' is defined multiple times", conflict.sourceLocation)
  }

  // 3.3 All types and directives defined within a schema must not have a name which begins with "__"
  directiveDefinitions.forEach { name, definition ->
    if (name.startsWith("__")) {
      throw SchemaValidationException("names starting with '__' are reserved for introspection", definition.sourceLocation)
    }
  }
}

/**
 * This is not in the specification per-se but in our use case, that will help catch some cases when users mistake
 * graphql operations for schemas
 */
private fun GQLDocument.validateNotExecutable() {
  definitions.firstOrNull { it is GQLOperationDefinition || it is GQLFragmentDefinition }
      ?.let {
        throw SchemaValidationException("Found an executable definition. Schemas should not contain operations or fragments.", it.sourceLocation)
      }
}

