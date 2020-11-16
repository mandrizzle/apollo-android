package com.apollographql.apollo.gradle.internal

import com.apollographql.apollo.compiler.parser.graphql.ast.GQLDocument
import com.apollographql.apollo.compiler.parser.graphql.ast.parseAsSchema
import com.apollographql.apollo.compiler.parser.graphql.ast.toGQLDocument
import com.apollographql.apollo.compiler.parser.graphql.ast.toUtf8
import com.apollographql.apollo.compiler.parser.introspection.IntrospectionSchema
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import toIntrospectionSchema
import java.io.File

/**
 * A task to download a schema either from introspection or from the registry.
 *
 * This task can either be configured from the command line or from the gradle scripts
 */
abstract class ApolloDownloadSchemaTask : DefaultTask() {
  @get:Optional
  @get:Input
  @get:Option(option = "endpoint", description = "url of the GraphQL endpoint")
  abstract val endpoint: Property<String>

  @get:Optional
  @get:Input
  @get:Option(option = "graph", description = "The identifier of the Apollo graph used to download the schema.")
  abstract val graph: Property<String>

  @get:Optional
  @get:Input
  @get:Option(option = "key", description = "The Apollo API key. See https://www.apollographql.com/docs/studio/api-keys/ for more information on how to get your API key.")
  abstract val key: Property<String>

  @get:Optional
  @get:Input
  @get:Option(option = "graphVariant", description = "The variant of the Apollo graph used to download the schema.")
  abstract val graphVariant: Property<String>

  @get:Input
  @get:Optional
  @get:Option(option = "schema", description = "path where the schema will be downloaded, relative to the current working directory")
  abstract val schema: Property<String>

  @get:Optional
  @get:Input
  @set:Option(option = "header", description = "headers in the form 'Name: Value'")
  var header = emptyList<String>() // cannot be abstract for @Option to work

  init {
    /**
     * We cannot know in advance if the backend schema changed so don't cache or mark this task up-to-date
     * This code actually redundant because the task has no output but adding it make it explicit.
     */
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
  }

  @TaskAction
  fun taskAction() {

    val endpointUrl = endpoint.getOrNull()

    val schema = schema.getOrNull()?.let { File(it) } // commandline is resolved relative to cwd
    check(schema != null) {
      "ApolloGraphQL: please specify where to download the schema with --schema"
    }
    val headers = header.toMap()

    var introspectionSchema: String? = null
    var sdlSchema: String? = null

    val key = key.getOrNull()
    var graph = graph.getOrNull()
    val graphVariant = graphVariant.getOrNull()

    if (graph == null && key != null && key.startsWith("service:")) {
      // Fallback to reading the graph from the key
      // This will not work with user keys
      graph = key.split(":")[1]
    }

    if (endpointUrl != null) {
      introspectionSchema = SchemaDownloader.downloadIntrospection(
          endpoint = endpointUrl,
          headers = headers,
      )
    } else if (graph != null) {
      check (key != null) {
        "ApolloGraphQL: please define --key to download graph $graph"
      }
      sdlSchema = SchemaDownloader.downloadRegistry(
          graph = graph,
          key = key,
          variant = graphVariant ?: "current"
      )
    } else {
      throw IllegalArgumentException("ApolloGraphQL: either --endpoint or --graph is required")
    }

    schema.parentFile?.mkdirs()

    if (schema.extension.toLowerCase() == "json") {
      if (introspectionSchema == null) {
        introspectionSchema = GQLDocument.parseAsSchema(sdlSchema!!).toIntrospectionSchema().wrap().toJson()
      }
      schema.writeText(introspectionSchema)
    } else {
      if (sdlSchema == null) {
        sdlSchema = IntrospectionSchema(introspectionSchema!!.byteInputStream()).toGQLDocument().toUtf8()
      }
      schema.writeText(sdlSchema)
    }
  }

  private fun List<String>.toMap(): Map<String, String> {
    return map {
      val index = it.indexOf(':')
      check(index > 0 && index < it.length - 1) {
        "header should be in the form 'Name: Value'"
      }

      it.substring(0, index).trim() to it.substring(index + 1, it.length).trim()
    }.toMap()
  }
}
