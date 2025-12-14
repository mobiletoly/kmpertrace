package dev.goquick.kmpertrace.analysis

/**
 * `log stream` / Xcode console prints a *single* unified-log entry that contains embedded newlines
 * as multiple physical lines: the first line has the timestamp/process header, the following lines
 * are continuations without that header.
 *
 * We need to reassemble those physical lines back into the original entry string so that downstream
 * chunk reassembly (kmpert markers) and structured framing can operate on whole entries.
 */
internal class IosUnifiedLogMultilineGrouper {
    private val buffer = StringBuilder()
    private var hasEntry = false

    // Examples covered:
    // - 2025-12-08 23:18:36.143963-0500  localhost powerd[333]: ...
    // - 12:34:56.789 MyApp[123:4567] <Info> ...
    private val syslogHead = Regex(
        "^\\s*\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+(?:[+-]\\d{4})?\\s+\\S+\\s+[^\\[]+\\[\\d+(?::\\d+)?\\]:\\s+.*$"
    )
    private val compactHead = Regex(
        "^\\s*\\d{2}:\\d{2}:\\d{2}\\.\\d+\\s+[^\\[]+\\[\\d+(?::\\d+)?\\]\\s+.*$"
    )

    fun feed(line: String): List<String> {
        val isHeader = syslogHead.matches(line) || compactHead.matches(line)
        if (isHeader) {
            val out = flush()
            buffer.clear()
            buffer.append(line)
            hasEntry = true
            return out
        }

        if (!hasEntry) {
            // Not a unified-log stream (or we started mid-entry); pass through.
            return listOf(line)
        }

        buffer.append('\n').append(line)
        return emptyList()
    }

    fun flush(): List<String> {
        if (!hasEntry) return emptyList()
        val out = buffer.toString()
        buffer.clear()
        hasEntry = false
        return listOf(out)
    }

    fun reset() {
        buffer.clear()
        hasEntry = false
    }
}

