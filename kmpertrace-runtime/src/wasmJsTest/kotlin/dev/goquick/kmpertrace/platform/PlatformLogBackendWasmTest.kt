package dev.goquick.kmpertrace.platform

import dev.goquick.kmpertrace.core.LogRecordKind
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.core.StructuredLogRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class PlatformLogBackendWasmTest {

    @Test
    fun formatLogLine_includes_error_fields_and_stack() {
        val record = StructuredLogRecord(
            timestamp = Instant.parse("2025-01-02T03:04:05Z"),
            level = Level.ERROR,
            loggerName = "WasmLogger",
            message = "failed",
            traceId = "trace-wasm",
            spanId = "span-wasm",
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
    fun structured_suffix_has_single_pipe_separator() {
        val record = StructuredLogRecord(
            timestamp = Instant.parse("2025-01-02T03:04:05Z"),
            level = Level.INFO,
            loggerName = "WasmLogger",
            message = "hello",
            traceId = null,
            spanId = null,
            logRecordKind = LogRecordKind.LOG
        )
        val rendered = formatLogLine(record)
        assertEquals(1, rendered.windowed(size = 2, step = 1).count { it == "|{" })
        assertTrue(!rendered.contains(" | |{"), "double pipe separator should not appear: $rendered")
    }
}
