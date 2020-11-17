package com.apollographql.apollo.compiler.model

import com.apollographql.apollo.compiler.parser.gql.GQLTypeDefinition

interface ModelNode {
  val children: List<ModelNode>
}

class Model(
    override val children: List<Field>
): ModelNode {
}

class Field(
    val name: String,
    val typeDefinition: GQLTypeDefinition,
    override val children: List<ModelNode>
): ModelNode {
}