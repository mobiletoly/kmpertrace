package dev.goquick.kmpertrace.analysis

/**
 * Reassembles chunked log lines marked with `(chunkId:kmpert...)` or `(chunkId:kmpert!)`
 * at the very end of the line. Returns complete reconstructed lines ready for parsing.
 */
class ChunkAssembler {
    private val buffers = mutableMapOf<String, StringBuilder>()
    private val markerRegex =
        Regex("""\(([\da-fA-F]{4,32}):kmpert(\.\.\.|!)\)$""")

    /**
    * Feed one raw line. May return zero or more completed lines.
    */
    fun feed(line: String): List<String> {
        val match = markerRegex.find(line.trimEnd())
        if (match == null) {
            // Not chunked
            return listOf(line)
        }
        val chunkId = match.groupValues[1]
        val kind = match.groupValues[2] // "..." or "!"
        val content = line.removeSuffix(match.value)
        return when (kind) {
            "..." -> {
                val buf = buffers.getOrPut(chunkId) { StringBuilder() }
                buf.append(content)
                emptyList()
            }
            "!" -> {
                val buf = buffers[chunkId] ?: return emptyList()
                buf.append(content)
                buffers.remove(chunkId)
                listOf(buf.toString().trimEnd())
            }
            else -> emptyList()
        }
    }

    fun reset() {
        buffers.clear()
    }
}
