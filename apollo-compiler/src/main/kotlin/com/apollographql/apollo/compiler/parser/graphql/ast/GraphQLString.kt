package com.apollographql.apollo.compiler.parser.graphql.ast

object GraphQLString {
  fun decodeSingleQuoted(string: String): String {
    val writer = StringBuilder(string.length)
    var i = 0
    while (i < string.length) {
      val c = string[i]
      i += 1
      if (c != '\\') {
        writer.append(c)
        continue
      }
      val escaped = string[i]
      when (escaped) {
        '"' -> {
          writer.append('"')
          i += 1
          continue
        }
        '/' -> {
          writer.append('/')
          i += 1
          continue
        }
        '\\' -> {
          writer.append('\\')
          i += 1
          continue
        }
        'b' -> {
          writer.append('\b')
          i += 1
          continue
        }
        'f' -> {
          writer.append('\u000C'.toInt())
          i += 1
          continue
        }
        'n' -> {
          writer.append('\n'.toInt())
          i += 1
          continue
        }
        'r' -> {
          writer.append('\r')
          i += 1
          continue
        }
        't' -> {
          writer.append('\t')
          i += 1
          continue
        }
        'u' -> {
          val codepoint = string.substring(i + 1, i + 5).toInt(16)
          writer.appendCodePoint(codepoint)
          i += 5
          continue
        }
        else -> throw IllegalStateException("Bad escaped character: $c")
      }
    }
    return writer.toString()
  }

  fun decodeTripleQuoted(string: String): String {
    return string.replace("\\\"\"\"", "\"\"\"").trimIndent()
  }

  fun encodeSingleQuoted(string: String): String {
    return string
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
  }

  fun encodeTripleQuoted(string: String): String {
    return string
        .replace("\"\"\"", "\\\"\"\"")
  }
}