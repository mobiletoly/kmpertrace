package dev.goquick.kmpertrace.analysis

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
    private var bufferHasStructured: Boolean = false
    private var canCoalesceMore: Boolean = true

    /**
     * Feed a new line. Zero or more collapsed lines may be emitted.
     */
    fun feed(line: String): List<String> {
        val structuredSuffix = line.contains("|{")

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

        if (structuredSuffix) {
            // If we were collecting a multi-line message without the suffix yet, finalize it with this suffix.
            if (currentKey != null && key == currentKey && !bufferHasStructured) {
                buffer!!.append('\n').append(messagePart)
                val emitted = buildCurrent()
                reset()
                return listOf(emitted)
            }
            // Otherwise, flush any previous message and emit this line on its own.
            val flushed = flushCurrent()
            currentKey = key
            buffer = StringBuilder(messagePart)
            bufferHasStructured = true
            canCoalesceMore = false
            val emitted = buildCurrent()
            reset()
            return flushed + emitted
        }

        if (currentKey == null) {
            // Buffer only when this looks like a multi-line payload we want to reconstruct (common for KmperTrace
            // logs where the human message already includes the tag prefix). Otherwise, emit immediately so
            // single raw lines don't get "stuck" waiting for a next line.
            if (shouldStartBuffer(messagePart, key.tag)) {
                currentKey = key
                buffer = StringBuilder(messagePart)
                bufferHasStructured = false
                canCoalesceMore = true
                return emptyList()
            }
            return listOf(buildLine(key, messagePart))
        }

        return if (key == currentKey) {
            if (canCoalesceMore && shouldCoalesce(messagePart, key.tag)) {
                buffer!!.append('\n').append(messagePart)
                emptyList()
            } else {
                // Same header can occur for distinct log statements in the same millisecond; avoid merging those.
                val emitted = buildCurrent()
                currentKey = key
                buffer = StringBuilder(messagePart)
                bufferHasStructured = false
                canCoalesceMore = true
                listOf(emitted)
            }
        } else {
            val emitted = buildCurrent()
            currentKey = key
            buffer = StringBuilder(messagePart)
            bufferHasStructured = false
            canCoalesceMore = true
            listOf(emitted)
        }
    }

    /**
     * Flush any buffered line at the end of the stream.
     */
    fun flush(): List<String> = flushCurrent()

    private fun flushCurrent(): List<String> {
        val emitted = currentKey?.let { listOf(buildCurrent()) } ?: emptyList()
        reset()
        return emitted
    }

    private fun buildCurrent(): String {
        val key = currentKey ?: return ""
        val msg = buffer?.toString().orEmpty()
        return buildLine(key, msg)
    }

    /**
     * Drop any buffered partial message (used by the TUI clear action).
     */
    fun reset() {
        currentKey = null
        buffer = null
        bufferHasStructured = false
        canCoalesceMore = true
    }

    private fun buildLine(key: Key, msg: String): String =
        "${key.ts} ${key.pid} ${key.tid} ${key.level} ${key.tag}: $msg"

    private fun shouldCoalesce(messagePart: String, tag: String): Boolean {
        val trimmed = messagePart.trimStart()
        if (trimmed.isEmpty()) return true
        // Common continuation patterns for stack traces and multi-line payloads.
        if (messagePart.first().isWhitespace()) return true
        if (trimmed.startsWith("at ")) return true
        if (trimmed.startsWith("Caused by:")) return true
        if (trimmed.startsWith("Suppressed:")) return true
        if (trimmed.startsWith("...")) return true

        // Many multi-line log payloads repeat the tag prefix inside the message.
        if (trimmed.startsWith("$tag:")) return true

        return false
    }

    private fun shouldStartBuffer(messagePart: String, tag: String): Boolean {
        val trimmed = messagePart.trimStart()
        // KmperTrace Android human prefix is typically "Tag: message", and logcat already prefixes with "Tag:",
        // producing "Tag: Tag: ...". Those messages are often the ones that get split and later end with |{...}|.
        return trimmed.startsWith("$tag:")
    }
}

