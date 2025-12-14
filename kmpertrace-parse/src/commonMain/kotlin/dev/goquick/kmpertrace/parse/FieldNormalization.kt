package dev.goquick.kmpertrace.parse

internal fun normalizeFields(fields: MutableMap<String, String>): MutableMap<String, String> {
    val src = fields["src"]
    val hasSrcComp = fields.containsKey("src_comp")
    val hasSrcOp = fields.containsKey("src_op")
    if (src != null && !hasSrcComp && !hasSrcOp) {
        val parts = src.split('/', limit = 2)
        if (parts.size == 2) {
            fields["src_comp"] = parts[0]
            fields["src_op"] = parts[1]
        } else {
            fields["src_comp"] = src
        }
        if (!fields.containsKey("src_hint")) {
            fields["src_hint"] = src
        }
    }
    if (src != null && !fields.containsKey("src_hint")) {
        fields["src_hint"] = src
    }

    fields["stack_trace"]?.let { raw ->
        fields["stack_trace"] = cleanStackTrace(raw)
    }

    return fields
}

// Strip common logcat prefixes (epoch, threadtime, brief/tag) that can appear on stack trace lines
// when adb splits multi-line messages. Leave lines intact if they don't match a known header.
private fun cleanStackTrace(raw: String): String =
    raw.split('\n').joinToString("\n") { stripLogcatPrefix(it.trimEnd()) }

private val logcatPrefixMatchers = listOf(
    Regex("^\\s*\\d{10}\\.\\d{3}\\s+\\d+\\s+\\d+\\s+[VDIWEF]\\s+[^:]+:\\s*"),
    Regex("^\\s*\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+\\s+\\d+\\s+\\d+\\s+[VDIWEF]/?\\s*[^:]*:\\s*"),
    Regex("^\\s*[VDIWEF]/[^:]+:\\s*")
)

private fun stripLogcatPrefix(line: String): String {
    for (regex in logcatPrefixMatchers) {
        val match = regex.find(line)
        if (match != null && match.range.first == 0) {
            val rest = line.substring(match.value.length)
            return normalizeStackLine(rest)
        }
    }
    val simple = line.indexOf(": ")
    if (simple >= 0) return normalizeStackLine(line.substring(simple + 2))
    return line
}

private fun normalizeStackLine(rest: String): String =
    if (rest.startsWith("at ")) "    $rest" else rest

