package dev.goquick.kmpertrace.parse

internal fun parseLogfmt(input: String): Map<String, String> {
    val result = linkedMapOf<String, String>()
    var index = 0
    val length = input.length

    fun skipSpaces() {
        while (index < length && input[index].isWhitespace()) index++
    }

    while (index < length) {
        skipSpaces()
        if (index >= length) break

        val keyStart = index
        while (index < length && input[index] != '=' && !input[index].isWhitespace()) {
            index++
        }
        if (index >= length || input[index] != '=') {
            while (index < length && !input[index].isWhitespace()) index++
            continue
        }
        val key = input.substring(keyStart, index)
        index++ // '='
        if (index >= length) {
            result[key] = ""
            break
        }

        val value = if (input[index] == '"') {
            index++
            val sb = StringBuilder()
            while (index < length) {
                val ch = input[index]
                if (ch == '\\' && index + 1 < length) {
                    sb.append(input[index + 1])
                    index += 2
                    continue
                }
                if (ch == '"') {
                    index++
                    break
                }
                sb.append(ch)
                index++
            }
            sb.toString()
        } else {
            val valueStart = index
            while (index < length && !input[index].isWhitespace()) index++
            input.substring(valueStart, index)
        }

        result[key] = value
    }

    return result
}

