package com.apollographql.apollo.gradle.internal

import com.apollographql.apollo.compiler.parser.introspection.IntrospectionSchema
import com.apollographql.apollo.compiler.parser.introspection.toSDL
import com.squareup.moshi.JsonWriter
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer
import okio.sink
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

object SchemaDownloader {
  fun download(
      endpoint: String,
      schema: File,
      headers: Map<String, String>,
      introspectionQuery: String,
      readTimeoutSeconds: Long,
      connectTimeoutSeconds: Long
  ) {
    val byteArrayOutputStream = ByteArrayOutputStream()
    JsonWriter.of(byteArrayOutputStream.sink().buffer())
        .apply {
          beginObject()
          name("query")
          value(introspectionQuery)
          endObject()
          flush()
        }

    val body = byteArrayOutputStream.toByteArray().toRequestBody("application/json".toMediaTypeOrNull())
    val request = Request.Builder()
        .post(body)
        .apply {
          headers.entries.forEach {
            addHeader(it.key, it.value)
          }
        }
        .url(endpoint)
        .build()

    val response = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .build()
        .newCall(request).execute()

    if (!response.isSuccessful) {
      throw Exception("cannot get schema: ${response.code}:\n${response.body?.string()}")
    }

    schema.parentFile?.mkdirs()

    response.body.use { responseBody ->
      if (schema.extension.toLowerCase() == "json") {
        schema.writeText(responseBody!!.string())
      } else {
        IntrospectionSchema(responseBody!!.byteStream()).toSDL(schema)
      }
    }
  }
}