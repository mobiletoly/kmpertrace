package dev.goquick.kmpertrace.testutil

/**
 * Minimal logfmt-ish parser for KmperTrace's structured suffix (`|{ ... }|`), used in tests.
 *
 * Supports:
 * - unquoted values: `k=v`
 * - quoted values with escaped quotes: `k="a b \\\"c\\\""`
 * - quoted values with real newlines (e.g. `stack_trace`)
 */
internal fun parseStructuredSuffix(structuredSuffix: String): Map<String, String> {
    val start = structuredSuffix.indexOf("|{")
    val end = structuredSuffix.lastIndexOf("}|")
    require(start >= 0 && end >= 0 && end > start) { "Not a structured suffix: $structuredSuffix" }

    val inner = structuredSuffix.substring(start + 2, end).trim()
    val result = LinkedHashMap<String, String>()

    var i = 0
    fun skipSpaces() {
        while (i < inner.length && inner[i] == ' ') i++
    }

    skipSpaces()
    while (i < inner.length) {
        val keyStart = i
        while (i < inner.length && inner[i] != '=' && inner[i] != ' ') i++
        if (i >= inner.length || inner[i] != '=') break
        val key = inner.substring(keyStart, i)
        i++ // '='

        val value: String =
            if (i < inner.length && inner[i] == '"') {
                i++ // opening quote
                val sb = StringBuilder()
                while (i < inner.length) {
                    val ch = inner[i++]
                    when (ch) {
                        '"' -> break
                        '\\' -> {
                            if (i < inner.length) {
                                val next = inner[i++]
                                sb.append(next)
                            }
                        }

                        else -> sb.append(ch)
                    }
                }
                sb.toString()
            } else {
                val valueStart = i
                while (i < inner.length && inner[i] != ' ') i++
                inner.substring(valueStart, i)
            }

        result[key] = value
        skipSpaces()
    }

    return result
}

