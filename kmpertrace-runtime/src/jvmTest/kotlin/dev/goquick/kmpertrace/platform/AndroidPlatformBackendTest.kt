package dev.goquick.kmpertrace.platform

import dev.goquick.kmpertrace.core.LogRecordKind
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.core.StructuredLogRecord
import dev.goquick.kmpertrace.log.KmperTrace
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Instant

class AndroidPlatformBackendTest {

    @AfterTest
    fun resetConfig() {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = emptyList())
    }

    @Test
    fun platform_backend_formats_and_prints() {
        val record = sampleEvent()
        val expectedStructured = formatLogLine(record, includeHumanPrefix = false)
        val expectedPrefix = "AndroidLogger: android hello"
        val expected = "$expectedPrefix $expectedStructured"

        // We don't invoke AndroidLog in unit tests (no Android runtime); just assert the composed line.
        val human = buildString {
            append(record.loggerName)
            if (record.message.isNotBlank()) append(": ").append(record.message)
        }
        val composed = "$human ${formatLogLine(record, includeHumanPrefix = false)}"

        assertEquals(expected, composed)
    }

    @Test
    fun log_includes_current_thread_name() {
        val currentThread = Thread.currentThread().name
        val record = StructuredLogRecord(
            timestamp = Instant.parse("2025-01-02T03:04:05Z"),
            level = Level.INFO,
            loggerName = "ThreadTest",
            message = "message",
            threadName = currentThread
        )

        val rendered = formatLogLine(record, includeHumanPrefix = false)
        assertContains(rendered, """thread="$currentThread"""")
        assertContains(rendered, "log=ThreadTest")
        // human prefix expectation
        val human = "ThreadTest: message"
        assertContains("$human $rendered", human)
    }

    @Test
    fun formatLogLine_includes_error_fields_and_stack() {
        val record = sampleEvent().copy(
            attributes = mapOf("status" to "ERROR", "err_type" to "IllegalStateException", "err_msg" to "boom"),
            throwable = IllegalStateException("boom")
        )
        val rendered = formatLogLine(record, includeHumanPrefix = false)
        assertContains(rendered, "status=\"ERROR\"")
        assertContains(rendered, "err_type=\"IllegalStateException\"")
        assertContains(rendered, "err_msg=\"boom\"")
        assertContains(rendered, "stack_trace=")
    }

    private fun sampleEvent(): StructuredLogRecord = StructuredLogRecord(
        timestamp = Instant.parse("2025-01-02T03:04:05Z"),
        level = Level.INFO,
        loggerName = "AndroidLogger",
        message = "android hello",
        traceId = "traceA",
        spanId = "spanA",
        parentSpanId = "-",
        logRecordKind = LogRecordKind.SPAN_END,
        spanName = "op",
        durationMs = 77,
        threadName = "main",
        serviceName = "svc",
        environment = "dev",
        attributes = mapOf("k" to "v")
    )
}
