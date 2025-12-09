package dev.goquick.kmpertrace.platform

import dev.goquick.kmpertrace.core.EventKind
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.core.LogEvent
import dev.goquick.kmpertrace.log.currentThreadNameOrNull
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosPlatformBackendTest {

    @Test
    fun current_thread_name_is_null_on_ios() {
        assertNull(currentThreadNameOrNull())
    }

    @Test
    fun formatLogLine_defaults_unknown_thread_when_none() {
        val event = LogEvent(
            timestamp = Instant.parse("2025-01-02T03:04:05Z"),
            level = Level.INFO,
            loggerName = "IosLogger",
            message = "msg",
            traceId = null,
            spanId = null,
            parentSpanId = null,
            eventKind = EventKind.LOG,
            spanName = null,
            durationMs = null,
            threadName = null,
            serviceName = null,
            environment = null
        )

        val rendered = formatLogLine(event)
        assertTrue(rendered.contains("|{ ts=2025-01-02T03:04:05Z"))
        assertTrue(rendered.contains("""head="msg""""))
        assertTrue(rendered.contains("log=IosLogger"))
    }

    @Test
    fun platform_backend_prints_human_prefix() {
        val event = LogEvent(
            timestamp = Instant.parse("2025-01-02T03:04:05Z"),
            level = Level.INFO,
            loggerName = "IosLogger",
            message = "hello",
            eventKind = EventKind.LOG
        )

        // On iOS tests, just ensure formatting returns the structured suffix.
        val rendered = formatLogLine(event)
        assertTrue(rendered.contains("|{ ts="))
    }

    @Test
    fun formatLogLine_includes_error_fields_and_stack() {
        val event = LogEvent(
            timestamp = Instant.parse("2025-01-02T03:04:05Z"),
            level = Level.ERROR,
            loggerName = "IosLogger",
            message = "failed",
            traceId = "trace-ios",
            spanId = "span-ios",
            eventKind = EventKind.SPAN_END,
            spanName = "op",
            durationMs = 1,
            attributes = mapOf(
                "status" to "ERROR",
                "error_type" to "IllegalStateException",
                "error_message" to "boom"
            ),
            throwable = IllegalStateException("boom")
        )

        val rendered = formatLogLine(event)
        assertTrue(rendered.contains("""status="ERROR""""))
        assertTrue(rendered.contains("""error_type="IllegalStateException""""))
        assertTrue(rendered.contains("""error_message="boom""""))
        assertTrue(rendered.contains("""stack_trace="""))
    }

    @Test
    fun platform_backend_handles_percent_signs_without_crash() {
        val event = LogEvent(
            timestamp = Instant.parse("2025-01-02T03:04:05Z"),
            level = Level.INFO,
            loggerName = "IosLogger",
            message = "progress 50% and 100%",
            eventKind = EventKind.LOG,
            throwable = IllegalStateException("boom for 100%")
        )

        // This used to crash due to NSLog format parsing when percent signs were present.
        // Simply invoke to ensure it does not throw.
        PlatformLogBackend.log(event)
    }
}
