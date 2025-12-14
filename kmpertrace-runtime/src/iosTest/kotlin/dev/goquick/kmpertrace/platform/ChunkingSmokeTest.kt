package dev.goquick.kmpertrace.platform

import dev.goquick.kmpertrace.core.LogRecordKind
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.core.StructuredLogRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ChunkingSmokeTest {
    @Test
    fun very_long_log_is_split_into_chunk_marked_lines() {
        val longMsg = buildString {
            append("prefix ")
            repeat(120) { append("0123456789") }
        }
        val record = StructuredLogRecord(
            timestamp = Instant.parse("2025-01-02T03:04:05Z"),
            level = Level.INFO,
            loggerName = "IosLogger",
            message = longMsg,
            logRecordKind = LogRecordKind.LOG
        )
        val base = "IosLogger: $longMsg |{ ts=${record.timestamp} lvl=info head=\"prefix 01234567\" log=IosLogger }|"

        val chunkId = "deadbeef"
        val chunks = PlatformLogSink.frameIosChunks(
            line = base,
            maxLogLineChars = 200,
            chunkSize = 80,
            chunkId = chunkId
        )

        assertTrue(chunks.size > 1, "expected chunking for long line")
        chunks.dropLast(1).forEach { chunk ->
            assertTrue(chunk.endsWith("($chunkId:kmpert...)"), "expected continuation marker at end, got: $chunk")
        }
        assertTrue(chunks.last().endsWith("($chunkId:kmpert!)"), "expected final marker at end, got: ${chunks.last()}")

        val reconstructed = chunks.joinToString("") { chunk ->
            chunk
                .replace(Regex("""\s*\($chunkId:kmpert(?:\.\.\.|!)\)$"""), "")
        }
        assertEquals(base, reconstructed)
    }
}
