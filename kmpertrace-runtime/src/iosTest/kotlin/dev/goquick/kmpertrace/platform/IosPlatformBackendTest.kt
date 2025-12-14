package dev.goquick.kmpertrace.platform

import dev.goquick.kmpertrace.core.LogRecordKind
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.core.StructuredLogRecord
import dev.goquick.kmpertrace.log.currentThreadNameOrNull
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosPlatformBackendTest {

    @Test
    fun current_thread_name_is_null_on_ios() {
        assertNull(currentThreadNameOrNull())
    }

    @Test
    fun formatLogLine_defaults_unknown_thread_when_none() {
        val record = StructuredLogRecord(
            timestamp = Instant.parse("2025-01-02T03:04:05Z"),
            level = Level.INFO,
            loggerName = "IosLogger",
            message = "msg",
            traceId = null,
            spanId = null,
            parentSpanId = null,
            logRecordKind = LogRecordKind.LOG,
            spanName = null,
            durationMs = null,
            threadName = null,
            serviceName = null,
            environment = null
        )

        val rendered = formatLogLine(record)
        assertTrue(rendered.contains("|{ ts=2025-01-02T03:04:05Z"))
        assertTrue(rendered.contains("""head="msg""""))
        assertTrue(rendered.contains("log=IosLogger"))
    }

    @Test
    fun platform_backend_prints_human_prefix() {
        val record = StructuredLogRecord(
            timestamp = Instant.parse("2025-01-02T03:04:05Z"),
            level = Level.INFO,
            loggerName = "IosLogger",
            message = "hello",
            logRecordKind = LogRecordKind.LOG
        )

        // On iOS tests, just ensure formatting returns the structured suffix.
        val rendered = formatLogLine(record)
        assertTrue(rendered.contains("|{ ts="))
    }

    @Test
    fun formatLogLine_includes_error_fields_and_stack() {
        val record = StructuredLogRecord(
            timestamp = Instant.parse("2025-01-02T03:04:05Z"),
            level = Level.ERROR,
            loggerName = "IosLogger",
            message = "failed",
            traceId = "trace-ios",
            spanId = "span-ios",
            logRecordKind = LogRecordKind.SPAN_END,
            spanName = "op",
            durationMs = 1,
            attributes = mapOf(
                "status" to "ERROR",
                "err_type" to "IllegalStateException",
                "err_msg" to "boom"
            ),
            throwable = IllegalStateException("boom")
        )

        val rendered = formatLogLine(record)
        assertTrue(rendered.contains("""status="ERROR""""))
        assertTrue(rendered.contains("""err_type="IllegalStateException""""))
        assertTrue(rendered.contains("""err_msg="boom""""))
        assertTrue(rendered.contains("""stack_trace="""))
    }

    @Test
    fun platform_backend_escapes_percent_signs_for_nslog_format_string() {
        val raw = "progress 50% and 100% (boom for 100%)"
        val escaped = escapeForNsLogFormat(raw)
        assertTrue(
            !containsBarePercent(escaped),
            "expected no bare % in escaped string, got: $escaped"
        )
    }

    private fun containsBarePercent(text: String): Boolean {
        var idx = 0
        while (idx < text.length) {
            if (text[idx] == '%') {
                val next = text.getOrNull(idx + 1)
                if (next != '%') return true
                idx += 2
                continue
            }
            idx++
        }
        return false
    }
}
