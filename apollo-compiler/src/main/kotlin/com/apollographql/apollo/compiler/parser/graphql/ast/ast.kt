/**
 * The GraphQL AST definition
 *
 * This is all in one file so we can use sealed classes. Extensions are in gqlxyz.kt
 */
package com.apollographql.apollo.compiler.parser.graphql.ast

import okio.BufferedSink

/**
 * A node in the GraphQL AST.
 *
 * For simplicity, not all tokens are mapped to GQLNodes and simple tokens such as name or description are directly properties on their composite node.
 *
 * Whitespace tokens are not mapped to GQLNodes so this AST will not be able to preserve formatting upon modification.
 */
interface GQLNode {
  val sourceLocation: SourceLocation

  /**
   * The children of this node.
   *
   * Terminal nodes won't have any children.
   */
  val children: List<GQLNode>
  fun write(bufferedSink: BufferedSink)
}

/**
 * depth first traversal
 */
fun GQLNode.visit(block: (GQLNode) -> Unit) {
  block(this)
  children.forEach {
    it.visit(block)
  }
}

/**
 * depth first traversal
 */
inline fun <reified T : GQLNode> GQLNode.visitIsInstance(block: (T) -> Unit) {
  (this as? T)?.let { block(it) }

  children.filterIsInstance<T>().forEach {
    block(it)
  }
}

interface GQLNamed {
  val name: String
}

interface GQLDescribed {
  val description: String?
}

interface GQLDefinition : GQLNode
interface GQLTypeSystemExtension : GQLNode
interface GQLTypeExtension : GQLTypeSystemExtension, GQLNamed

sealed class GQLSelection : GQLNode

data class GQLDocument(
    val definitions: List<GQLDefinition>,
    val filePath: String?
) : GQLNode {
  override val sourceLocation: SourceLocation = SourceLocation(0, 0, filePath)
  override val children = definitions

  override fun write(bufferedSink: BufferedSink) {
    definitions.join(bufferedSink = bufferedSink, separator = "\n")
  }

  companion object
}

data class GQLOperationDefinition(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val operationType: String,
    val name: String?,
    val variableDefinitions: List<GQLVariableDefinition>,
    val directives: List<GQLDirective>,
    val selectionSet: GQLSelectionSet
) : GQLDefinition {
  override val children = variableDefinitions + directives + selectionSet

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8(operationType)
      if (name != null) {
        writeUtf8(" ")
        writeUtf8(name)
        if (variableDefinitions.isNotEmpty()) {
          variableDefinitions.join(bufferedSink = bufferedSink, separator = ", ", prefix = "(", postfix = ")")
        }
      }
      if (directives.isNotEmpty()) {
        writeUtf8(" ")
        directives.join(bufferedSink)
      }
      if (selectionSet.selections.isNotEmpty()) {
        writeUtf8(" ")
        selectionSet.write(bufferedSink)
      }
    }
  }
}

data class GQLFragmentDefinition(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    override val name: String,
    val directives: List<GQLDirective>,
    val typeCondition: GQLNamedType,
    val selectionSet: GQLSelectionSet
) : GQLDefinition, GQLNamed {

  override val children = directives + selectionSet + typeCondition

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8("fragment $name on ${typeCondition.name}")
      if (directives.isNotEmpty()) {
        writeUtf8(" ")
        directives.join(bufferedSink)
      }
      if (selectionSet.selections.isNotEmpty()) {
        writeUtf8(" ")
        selectionSet.write(bufferedSink)
      }
    }
  }
}

data class GQLSchemaDefinition(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val description: String?,
    val directives: List<GQLDirective>,
    val rootOperationTypeDefinitions: List<GQLOperationTypeDefinition>
) : GQLDefinition {

  override val children = directives + rootOperationTypeDefinitions

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      if (description != null) writeUtf8("\"\"\"${GraphQLString.encodeTripleQuoted(description)}\"\"\"\n")
      if (directives.isNotEmpty()) {
        directives.join(bufferedSink)
        writeUtf8(" ")
      }
      writeUtf8("schema ")
      rootOperationTypeDefinitions.join(bufferedSink, prefix = "{\n", separator = "\n", postfix = "\n}\n")
    }
  }

}

sealed class GQLTypeDefinition : GQLDefinition, GQLNamed, GQLDescribed
data class GQLInterfaceTypeDefinition(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    override val description: String?,
    override val name: String,
    val implementsInterfaces: List<String>,
    val directives: List<GQLDirective>,
    val fields: List<GQLFieldDefinition>
) : GQLTypeDefinition() {

  override val children: List<GQLNode> = directives + fields

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      if (description != null) writeUtf8("\"\"\"${GraphQLString.encodeTripleQuoted(description)}\"\"\"\n")
      writeUtf8("interface $name ${implementsInterfaces.joinToString(" ")}")
      if (directives.isNotEmpty()) {
        writeUtf8(" ")
        directives.join(bufferedSink)
      }
      if (fields.isNotEmpty()) {
        writeUtf8(" ")
        fields.join(bufferedSink, prefix = "{\n", separator = "\n", postfix = "\n}\n")
      }
    }
  }
}

data class GQLObjectTypeDefinition(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    override val description: String?,
    override val name: String,
    val implementsInterfaces: List<String>,
    val directives: List<GQLDirective>,
    val fields: List<GQLFieldDefinition>,
) : GQLTypeDefinition() {

  override val children: List<GQLNode> = directives + fields

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      if (description != null) writeUtf8("\"\"\"${GraphQLString.encodeTripleQuoted(description)}\"\"\"\n")
      writeUtf8("type $name")
      if (implementsInterfaces.isNotEmpty()) {
        writeUtf8(" implements ")
        writeUtf8(implementsInterfaces.joinToString(" "))
      }
      if (directives.isNotEmpty()) {
        writeUtf8(" ")
        directives.join(bufferedSink)
      }
      if (fields.isNotEmpty()) {
        fields.join(bufferedSink, prefix = "{\n", separator = "\n", postfix = "\n}\n")
      }
    }
  }
}

data class GQLInputObjectTypeDefinition(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    override val description: String?,
    override val name: String,
    val directives: List<GQLDirective>,
    val inputFields: List<GQLInputValueDefinition>
) : GQLTypeDefinition() {

  override val children: List<GQLNode> = directives + inputFields

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      if (description != null) writeUtf8("\"\"\"${GraphQLString.encodeTripleQuoted(description)}\"\"\"\n")
      writeUtf8("input $name")
      if (directives.isNotEmpty()) {
        writeUtf8(" ")
        directives.join(bufferedSink)
      }
      if (inputFields.isNotEmpty()) {
        writeUtf8(" ")
        inputFields.join(bufferedSink, prefix = "{\n", separator = "\n", postfix = "\n}\n")
      }
    }
  }
}

data class GQLScalarTypeDefinition(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    override val description: String?,
    override val name: String,
    val directives: List<GQLDirective>
) : GQLTypeDefinition() {

  override val children = directives

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      if (description != null) writeUtf8("\"\"\"${GraphQLString.encodeTripleQuoted(description)}\"\"\"\n")
      writeUtf8("scalar $name")
      if (directives.isNotEmpty()) {
        writeUtf8(" ")
        directives.join(bufferedSink)
      }
    }
  }
}

data class GQLEnumTypeDefinition(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    override val description: String?,
    override val name: String,
    val directives: List<GQLDirective>,
    val enumValues: List<GQLEnumValueDefinition>
) : GQLTypeDefinition() {

  override val children: List<GQLNode> = directives + enumValues

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      if (description != null) writeUtf8("\"\"\"${GraphQLString.encodeTripleQuoted(description)}\"\"\"\n")
      writeUtf8("enum $name")
      if (directives.isNotEmpty()) {
        writeUtf8(" ")
        directives.join(bufferedSink)
      }
      if (enumValues.isNotEmpty()) {
        writeUtf8(" ")
        enumValues.join(bufferedSink, prefix = "{\n", separator = "\n", postfix = "\n}\n")
      }
    }
  }
}

data class GQLUnionTypeDefinition(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    override val description: String?,
    override val name: String,
    val directives: List<GQLDirective>,
    val memberTypes: List<GQLNamedType>
) : GQLTypeDefinition() {

  override val children: List<GQLNode> = directives + memberTypes

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      if (description != null) writeUtf8("\"\"\"${GraphQLString.encodeTripleQuoted(description)}\"\"\"\n")
      writeUtf8("union $name")
      if (directives.isNotEmpty()) {
        writeUtf8(" ")
        directives.join(bufferedSink)
      }
      writeUtf8(" = ")
      memberTypes.join(bufferedSink, separator = "|")
    }
  }
}

data class GQLDirectiveDefinition(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val description: String?,
    val name: String,
    val arguments: List<GQLInputValueDefinition>,
    val repeatable: Boolean,
    val locations: List<GQLDirectiveLocation>
) : GQLDefinition {

  override val children: List<GQLNode> = arguments

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      if (description != null) writeUtf8("\"\"\"${GraphQLString.encodeTripleQuoted(description)}\"\"\"\n")
      writeUtf8("directive @$name")
      if (arguments.isNotEmpty()) {
        writeUtf8(" ")
        arguments.join(bufferedSink, prefix = "(", separator = ", ", postfix = ")")
      }
      if (repeatable) {
        writeUtf8(" repeatable")
      }
      writeUtf8(" on ${locations.joinToString("|")}")
    }
  }
}

data class GQLSchemaExtension(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val directives: List<GQLDirective>,
    val operationTypesDefinition: List<GQLOperationTypeDefinition>
) : GQLDefinition, GQLTypeSystemExtension {

  override val children = directives + operationTypesDefinition

  override fun write(bufferedSink: BufferedSink) {
    TODO("Not yet implemented")
  }
}

data class GQLEnumTypeExtension(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    override val name: String,
    val directives: List<GQLDirective>,
    val enumValues: List<GQLEnumValueDefinition>
) : GQLDefinition, GQLTypeExtension {

  override val children: List<GQLNode> = directives + enumValues

  override fun write(bufferedSink: BufferedSink) {
    TODO("Not yet implemented")
  }
}

data class GQLObjectTypeExtension(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    override val name: String,
    val directives: List<GQLDirective>,
    val fields: List<GQLFieldDefinition>
) : GQLDefinition, GQLTypeExtension {

  override val children: List<GQLNode> = directives + fields

  override fun write(bufferedSink: BufferedSink) {
    TODO("Not yet implemented")
  }
}

data class GQLInputObjectTypeExtension(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    override val name: String,
    val directives: List<GQLDirective>,
    val inputFields: List<GQLInputValueDefinition>
) : GQLDefinition, GQLTypeExtension {

  override val children: List<GQLNode> = directives + inputFields

  override fun write(bufferedSink: BufferedSink) {
    TODO("Not yet implemented")
  }
}

data class GQLScalarTypeExtension(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    override val name: String,
    val directives: List<GQLDirective>
) : GQLDefinition, GQLTypeExtension {

  override val children = directives

  override fun write(bufferedSink: BufferedSink) {
    TODO("Not yet implemented")
  }
}

data class GQLInterfaceTypeExtension(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    override val name: String,
    val fields: List<GQLFieldDefinition>
) : GQLDefinition, GQLTypeExtension, GQLNamed {

  override val children = fields

  override fun write(bufferedSink: BufferedSink) {
    TODO("Not yet implemented")
  }
}

data class GQLUnionTypeExtension(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    override val name: String,
    val directives: List<GQLDirective>,
    val memberTypes: List<GQLNamedType>
) : GQLDefinition, GQLTypeExtension {

  override val children: List<GQLNode> = directives + memberTypes

  override fun write(bufferedSink: BufferedSink) {
    TODO("Not yet implemented")
  }
}

data class GQLEnumValueDefinition(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val description: String?,
    override val name: String,
    val directives: List<GQLDirective>
) : GQLNode, GQLNamed {

  override val children = directives

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      if (description != null) writeUtf8("\"\"\"${GraphQLString.encodeTripleQuoted(description)}\"\"\"\n")
      writeUtf8("$name")
      if (directives.isNotEmpty()) {
        writeUtf8(" ")
        directives.join(bufferedSink)
      }

    }
  }
}

data class GQLFieldDefinition(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val description: String?,
    override val name: String,
    val arguments: List<GQLInputValueDefinition>,
    val type: GQLType,
    val directives: List<GQLDirective>
) : GQLNode, GQLNamed {

  override val children: List<GQLNode> = directives + arguments

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      if (description != null) writeUtf8("\"\"\"${GraphQLString.encodeTripleQuoted(description)}\"\"\"\n")
      writeUtf8(name)
      if (arguments.isNotEmpty()) {
        writeUtf8(" ")
        arguments.join(bufferedSink, prefix = "(", separator = ", ", postfix = ")")
      }
      writeUtf8(": ")
      type.write(bufferedSink)
      if (directives.isNotEmpty()) {
        writeUtf8(" ")
        directives.join(bufferedSink)
      }
    }
  }
}

data class GQLInputValueDefinition(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val description: String?,
    override val name: String,
    val directives: List<GQLDirective>,
    val type: GQLType,
    val defaultValue: GQLValue?
) : GQLNode, GQLNamed {

  override val children = directives

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      if (description != null) writeUtf8("\"\"\"${GraphQLString.encodeTripleQuoted(description)}\"\"\" ")
      writeUtf8("$name: ")
      type.write(bufferedSink)
      if (defaultValue != null) {
        writeUtf8(" = ")
        defaultValue.write(bufferedSink)
      }
      if (directives.isNotEmpty()) {
        writeUtf8(" ")
        directives.join(bufferedSink)
      }
    }
  }
}

data class GQLVariableDefinition(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val name: String,
    val type: GQLType,
    val defaultValue: GQLValue?,
    //val directives: List<GQLDirective>
) : GQLNode {

  override val children = mutableListOf<GQLNode>().apply {
    if (defaultValue != null) {
      add(defaultValue)
    }
  }

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8("${'$'}$name: ")
      type.write(bufferedSink)
      if (defaultValue != null) {
        writeUtf8(" = ")
        defaultValue.write(bufferedSink)
        writeUtf8(" ")
      }
      // TODO("support variable directives")
      // directives.join(bufferedSink)
    }
  }
}

data class GQLOperationTypeDefinition(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val operationType: String,
    val namedType: String
) : GQLNode {

  override val children = emptyList<GQLNode>()

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8("$operationType: $namedType")
    }
  }
}

data class GQLDirective(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    override val name: String,
    val arguments: GQLArguments
) : GQLNode, GQLNamed {

  override val children = listOf(arguments)

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8("@$name")
      arguments.write(bufferedSink)
    }
  }
}

data class GQLObjectField(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val name: String,
    val value: GQLValue
) : GQLNode {

  override val children = listOf(value)

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8("$name: ")
      value.write(bufferedSink)
    }
  }
}

data class GQLArgument(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val name: String,
    val value: GQLValue
) : GQLNode {

  override val children = listOf(value)

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8("$name: ")
      value.write(bufferedSink)
    }
  }
}

data class GQLSelectionSet(
    val selections: List<GQLSelection>,
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN
) : GQLNode {
  override val children = selections

  override fun write(bufferedSink: BufferedSink) {
    selections.join(bufferedSink, prefix = "{\n", separator = "\n", postfix = "\n}")
  }
}

data class GQLArguments(
    val arguments: List<GQLArgument>,
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
) : GQLNode {
  override val children: List<GQLNode> = arguments

  override fun write(bufferedSink: BufferedSink) {
    arguments.join(bufferedSink, prefix = "(", separator = ", ", postfix = ")")
  }
}

data class GQLField(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val alias: String?,
    val name: String,
    val arguments: GQLArguments?,
    val directives: List<GQLDirective>,
    val selectionSet: GQLSelectionSet?
) : GQLSelection() {

  override val children: List<GQLNode> = directives.toMutableList<GQLNode>().apply {
    if (selectionSet != null) {
      add(selectionSet)
    }
    if (arguments != null) {
      add(arguments)
    }
  }

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      if (alias != null) {
        writeUtf8("$alias: ")
      }
      writeUtf8(name)
      arguments?.write(bufferedSink)
      if (directives.isNotEmpty()) {
        writeUtf8(" ")
        directives.join(bufferedSink)
      }
      if (selectionSet != null) {
        writeUtf8(" ")
        selectionSet.write(bufferedSink)
      }
    }
  }
}

data class GQLInlineFragment(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val typeCondition: GQLNamedType,
    val directives: List<GQLDirective>,
    val selectionSet: GQLSelectionSet
) : GQLSelection() {

  override val children = directives + selectionSet + typeCondition

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8("... on ${typeCondition.name}")
      if (directives.isNotEmpty()) {
        writeUtf8(" ")
        directives.join(bufferedSink)
      }
      if (selectionSet.selections.isNotEmpty()) {
        writeUtf8(" ")
        selectionSet.write(bufferedSink)
      }
    }
  }
}

data class GQLFragmentSpread(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val name: String,
    val directives: List<GQLDirective>
) : GQLSelection() {

  override val children = directives

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8("...${name}")
      if (directives.isNotEmpty()) {
        writeUtf8(" ")
        directives.join(bufferedSink)
      }

    }
  }
}

sealed class GQLType : GQLNode

data class GQLNamedType(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    override val name: String
) : GQLType(), GQLNamed {

  override val children = emptyList<GQLNode>()

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8(name)
    }
  }
}

data class GQLNonNullType(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val type: GQLType
) : GQLType() {

  override val children = listOf(type)

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      type.write(bufferedSink)
      writeUtf8("!")
    }
  }
}

data class GQLListType(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val type: GQLType
) : GQLType() {

  override val children = listOf(type)

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8("[")
      type.write(bufferedSink)
      writeUtf8("]")
    }
  }
}


sealed class GQLValue : GQLNode
data class GQLVariableValue(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val name: String
) : GQLValue() {

  override val children = emptyList<GQLNode>()

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8("${'$'}$name")
    }
  }
}

data class GQLIntValue(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val value: Int
) : GQLValue() {

  override val children = emptyList<GQLNode>()

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8(value.toString())
    }
  }
}

data class GQLFloatValue(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val value: Double
) : GQLValue() {

  override val children = emptyList<GQLNode>()

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8(value.toString())
    }
  }
}

data class GQLStringValue(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val value: String
) : GQLValue() {

  override val children = emptyList<GQLNode>()

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8("\"${GraphQLString.encodeSingleQuoted(value)}\"")
    }
  }
}

data class GQLBooleanValue(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val value: Boolean
) : GQLValue() {

  override val children = emptyList<GQLNode>()

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8(value.toString())
    }
  }
}

data class GQLEnumValue(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val value: String
) : GQLValue() {

  override val children = emptyList<GQLNode>()

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8(value)
    }
  }
}

data class GQLListValue(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val values: List<GQLValue>
) : GQLValue() {

  override val children = values

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8("[")
      values.join(bufferedSink, ",")
      writeUtf8("]")
    }

  }
}

data class GQLObjectValue(
    override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN,
    val fields: List<GQLObjectField>
) : GQLValue() {

  override val children = fields

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8("{\n")
      fields.join(bufferedSink = bufferedSink, "\n")
      writeUtf8("\n}\n")
    }
  }
}

data class GQLNullValue(override val sourceLocation: SourceLocation = SourceLocation.UNKNOWN) : GQLValue() {

  override val children = emptyList<GQLNode>()

  override fun write(bufferedSink: BufferedSink) {
    with(bufferedSink) {
      writeUtf8("null")
    }
  }
}

private fun <T : GQLNode> List<T>.join(bufferedSink: BufferedSink, separator: String = " ", prefix: String = "", postfix: String = "") {
  bufferedSink.writeUtf8(prefix)
  forEachIndexed { index, t ->
    t.write(bufferedSink)
    if (index < size - 1) {
      bufferedSink.writeUtf8(separator)
    }
  }
  bufferedSink.writeUtf8(postfix)
}


enum class GQLDirectiveLocation {
  QUERY,
  MUTATION,
  SUBSCRIPTION,
  FIELD,
  FRAGMENT_DEFINITION,
  FRAGMENT_SPREAD,
  INLINE_FRAGMENT,
  VARIABLE_DEFINITION,
  TypeSystemDirectiveLocation,
  SCHEMA,
  SCALAR,
  OBJECT,
  FIELD_DEFINITION,
  ARGUMENT_DEFINITION,
  INTERFACE,
  UNION,
  ENUM,
  ENUM_VALUE,
  INPUT_OBJECT,
  INPUT_FIELD_DEFINITION,
}