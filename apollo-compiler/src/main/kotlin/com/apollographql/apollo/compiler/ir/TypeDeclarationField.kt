package com.apollographql.apollo.compiler.ir

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TypeDeclarationField(
    val name: String,
    val description: String = "",
    val type: String,
    val defaultValue: Any?
)
