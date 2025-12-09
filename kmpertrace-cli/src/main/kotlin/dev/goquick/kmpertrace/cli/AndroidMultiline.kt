package dev.goquick.kmpertrace.cli

/**
 * Groups consecutive Android logcat lines that share the same header (timestamp/pid/tid/level/tag)
 * into a single logical line. This helps reconstruct multi-line messages that logcat splits when
 * the original log contained embedded newlines.
 */
internal class AndroidMultilineGrouper {
    private data class Key(
        val ts: String,
        val pid: String,
        val tid: String,
        val level: String,
        val tag: String
    )

    // Matches lines produced by `adb logcat -v epoch --pid=...`
    private val epochRegex =
        Regex("^\\s*(\\d+\\.\\d+)\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEFA])\\s+([^:]+):\\s*(.*)$")

    private var currentKey: Key? = null
    private var buffer: StringBuilder? = null

    /**
     * Feed a new line. Zero or more collapsed lines may be emitted.
     */
    fun feed(line: String): List<String> {
        val match = epochRegex.find(line)
        if (match == null) {
            return flushCurrent().plus(line)
        }

        val key = Key(
            ts = match.groupValues[1],
            pid = match.groupValues[2],
            tid = match.groupValues[3],
            level = match.groupValues[4],
            tag = match.groupValues[5].trim()
        )
        val messagePart = match.groupValues[6]

        if (currentKey == null) {
            currentKey = key
            buffer = StringBuilder(messagePart)
            return emptyList()
        }

        return if (key == currentKey) {
            buffer!!.append('\n').append(messagePart)
            emptyList()
        } else {
            val emitted = buildCurrent()
            currentKey = key
            buffer = StringBuilder(messagePart)
            listOf(emitted)
        }
    }

    /**
     * Flush any buffered line at the end of the stream.
     */
    fun flush(): List<String> = flushCurrent()

    private fun flushCurrent(): List<String> {
        val emitted = currentKey?.let { listOf(buildCurrent()) } ?: emptyList()
        currentKey = null
        buffer = null
        return emitted
    }

    private fun buildCurrent(): String {
        val key = currentKey ?: return ""
        val msg = buffer?.toString().orEmpty()
        return "${key.ts} ${key.pid} ${key.tid} ${key.level} ${key.tag}: $msg"
    }
}

internal fun collapseAndroidMultiline(lines: Sequence<String>): Sequence<String> = sequence {
    val grouper = AndroidMultilineGrouper()
    lines.forEach { line ->
        grouper.feed(line).forEach { yield(it) }
    }
    grouper.flush().forEach { yield(it) }
}
