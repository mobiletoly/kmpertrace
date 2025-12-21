package dev.goquick.kmpertrace.parse

internal data class HumanInfo(val logger: String?, val message: String?)

private val logcatThreadTimeRegex =
    Regex("""^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+\d+\s+\d+\s+[VDIWEF]/?\s*(\S+):\s*(.*)$""")

private val logcatEpochRegex =
    Regex("""^\d+\.\d+\s+\d+\s+\d+\s+[VDIWEFA]\s+(\S+):\s*(.*)$""")

private val logcatBriefRegex =
    Regex("""^[VDIWEF]/\s*(\S+):\s*(.*)$""")

internal fun parseHumanPrefix(human: String): HumanInfo {
    if (human.isBlank()) return HumanInfo(null, null)
    var text = human.trim()
    val glyphs = listOf("▫️", "🔍", "ℹ️", "⚠️", "❌", "💥")
    glyphs.forEach { if (text.startsWith(it)) text = text.removePrefix(it).trimStart() }

    logcatThreadTimeRegex.matchEntire(text)?.let { m ->
        val logger = m.groupValues[1].takeIf { it.isNotEmpty() }
        val msg = m.groupValues[2].takeIf { it.isNotEmpty() }
        return HumanInfo(logger, msg)
    }
    logcatEpochRegex.matchEntire(text)?.let { m ->
        val logger = m.groupValues[1].takeIf { it.isNotEmpty() }
        val msg = m.groupValues[2].takeIf { it.isNotEmpty() }
        return HumanInfo(logger, msg)
    }
    logcatBriefRegex.matchEntire(text)?.let { m ->
        val logger = m.groupValues[1].takeIf { it.isNotEmpty() }
        val msg = m.groupValues[2].takeIf { it.isNotEmpty() }
        return HumanInfo(logger, msg)
    }

    if (text.startsWith("+++ ") || text.startsWith("--- ")) {
        return HumanInfo(null, text)
    }

    val colon = text.indexOf(':')
    if (colon > 0) {
        val logger = text.substring(0, colon).trim().takeIf { it.isNotEmpty() }
        val msg = text.substring(colon + 1).trim().takeIf { it.isNotEmpty() }
        return HumanInfo(logger, msg)
    }

    return HumanInfo(null, null)
}

internal fun stripLogcatPrefixesFromContinuationLines(humanPrefix: String): String {
    val lines = humanPrefix.split('\n')
    if (lines.size <= 1) return humanPrefix

    val cleaned = lines.mapIndexed { index, line ->
        if (index == 0) {
            line
        } else {
            stripLogcatContinuationPrefix(line) ?: line
        }
    }
    return cleaned.joinToString("\n")
}

internal fun resolveMessage(logger: String?, head: String?, humanPrefix: String, human: HumanInfo): String? {
    val trimmedHuman = humanPrefix.trim()
    if (!head.isNullOrBlank()) {
        if (!logger.isNullOrBlank()) {
            val anchor = "$logger: $head"
            val idxAnchor = trimmedHuman.indexOf(anchor)
            if (idxAnchor >= 0) {
                val idxHead = trimmedHuman.indexOf(head, idxAnchor)
                if (idxHead >= 0) {
                    val full = trimmedHuman.substring(idxHead).trim()
                    if (full.isNotEmpty()) return full
                }
            }
        }
        val idxHead = trimmedHuman.indexOf(head)
        if (idxHead >= 0) {
            val full = trimmedHuman.substring(idxHead).trim()
            if (full.isNotEmpty()) return full
        }
        if (head.isNotBlank()) return head
    }
    val humanMessage = human.message?.takeIf { it.isNotBlank() }
    if (humanMessage != null) return humanMessage
    return if (trimmedHuman.isNotEmpty()) trimmedHuman else null
}

private fun stripLogcatContinuationPrefix(line: String): String? {
    val trimmed = line.trimStart()
    logcatThreadTimeRegex.matchEntire(trimmed)?.let { m ->
        return m.groupValues[2]
    }
    logcatEpochRegex.matchEntire(trimmed)?.let { m ->
        return m.groupValues[2]
    }
    logcatBriefRegex.matchEntire(trimmed)?.let { m ->
        return m.groupValues[2]
    }
    return null
}
