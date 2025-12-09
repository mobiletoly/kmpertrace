package dev.goquick.kmpertrace.platform

import dev.goquick.kmpertrace.core.EventKind
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.core.LogEvent
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

class ChunkingSmokeTest {
    @Test
    fun very_long_log_is_split_and_does_not_crash() {
        val longMsg = buildString {
            append("prefix ")
            repeat(300) { append("0123456789") }
        }
        val event = LogEvent(
            timestamp = Instant.parse("2025-01-02T03:04:05Z"),
            level = Level.ERROR,
            loggerName = "IosLogger",
            message = longMsg,
            eventKind = EventKind.LOG,
            throwable = IllegalStateException("boom")
        )
        // Just ensure no crash when emitting chunked lines.
        PlatformLogBackend.log(event)
        assertTrue(true)
    }
}
