package dev.goquick.kmpertrace.parse

/**
 * Incrementally frames "human prefix + structured suffix" log entries.
 *
 * Input is assumed to arrive as lines, but structured entries may span multiple reads (e.g.
 * multiline `stack_trace` stored inside quotes with real newlines).
 *
 * This framer buffers until it sees a closing `}|` that occurs after the last `|{`, then emits
 * the buffered chunk up to and including `}|`. Any trailing text after the close marker is retained
 * and may form the start of the next entry.
 */
internal class StructuredSuffixFramer(
    private val maxBufferChars: Int = 50_000
) {
    private val buffer = StringBuilder()

    fun clear() {
        buffer.clear()
    }

    fun feed(line: String): List<String> {
        if (buffer.isNotEmpty()) buffer.append('\n')
        buffer.append(line)
        return drainCompleted()
    }

    fun flush(): List<String> {
        if (buffer.isEmpty()) return emptyList()
        return listOf(buffer.toString()).also { buffer.clear() }
    }

    private fun drainCompleted(): List<String> {
        val out = mutableListOf<String>()
        while (true) {
            val buffered = buffer.toString()
            val lastOpen = buffered.lastIndexOf("|{")
            val lastClose = buffered.lastIndexOf("}|")
            if (lastOpen == -1 || lastClose == -1 || lastClose <= lastOpen) break

            val candidate = buffered.substring(0, lastClose + 2)
            val trailing = buffered.substring(lastClose + 2).trimStart('\n')
            out += candidate

            buffer.clear()
            if (trailing.isNotEmpty()) {
                buffer.append(trailing)
                continue
            }
            break
        }

        if (buffer.length > maxBufferChars) {
            buffer.clear()
        }
        return out
    }
}

internal fun frameStructuredSuffixEntries(lines: Sequence<String>): Sequence<String> = sequence {
    val framer = StructuredSuffixFramer()
    for (line in lines) {
        framer.feed(line).forEach { yield(it) }
    }
    framer.flush().forEach { yield(it) }
}

