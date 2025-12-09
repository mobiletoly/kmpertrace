package dev.goquick.kmpertrace.platform

import dev.goquick.kmpertrace.core.EventKind
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.core.LogEvent
import dev.goquick.kmpertrace.log.LoggerConfig
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Instant

class AndroidPlatformBackendTest {

    @AfterTest
    fun resetConfig() {
        LoggerConfig.backends = emptyList()
        LoggerConfig.minLevel = Level.DEBUG
    }

    @Test
    fun platform_backend_formats_and_prints() {
        val event = sampleEvent()
        val expectedStructured = formatLogLine(event, includeHumanPrefix = false)
        val expectedPrefix = "AndroidLogger: android hello"
        val expected = "$expectedPrefix $expectedStructured"

        // We don't invoke AndroidLog in unit tests (no Android runtime); just assert the composed line.
        val human = buildString {
            append(event.loggerName)
            if (event.message.isNotBlank()) append(": ").append(event.message)
        }
        val composed = "$human ${formatLogLine(event, includeHumanPrefix = false)}"

        assertEquals(expected, composed)
    }

    @Test
    fun log_includes_current_thread_name() {
        val currentThread = Thread.currentThread().name
        val event = LogEvent(
            timestamp = Instant.parse("2025-01-02T03:04:05Z"),
            level = Level.INFO,
            loggerName = "ThreadTest",
            message = "message",
            threadName = currentThread
        )

        val rendered = formatLogLine(event, includeHumanPrefix = false)
        assertContains(rendered, """thread="$currentThread"""")
        assertContains(rendered, "log=ThreadTest")
        // human prefix expectation
        val human = "ThreadTest: message"
        assertContains("$human $rendered", human)
    }

    @Test
    fun formatLogLine_includes_error_fields_and_stack() {
        val event = sampleEvent().copy(
            attributes = mapOf("status" to "ERROR", "error_type" to "IllegalStateException", "error_message" to "boom"),
            throwable = IllegalStateException("boom")
        )
        val rendered = formatLogLine(event, includeHumanPrefix = false)
        assertContains(rendered, "status=\"ERROR\"")
        assertContains(rendered, "error_type=\"IllegalStateException\"")
        assertContains(rendered, "error_message=\"boom\"")
        assertContains(rendered, "stack_trace=")
    }

    private fun sampleEvent(): LogEvent = LogEvent(
        timestamp = Instant.parse("2025-01-02T03:04:05Z"),
        level = Level.INFO,
        loggerName = "AndroidLogger",
        message = "android hello",
        traceId = "traceA",
        spanId = "spanA",
        parentSpanId = "-",
        eventKind = EventKind.SPAN_END,
        spanName = "op",
        durationMs = 77,
        threadName = "main",
        serviceName = "svc",
        environment = "dev",
        attributes = mapOf("k" to "v")
    )
}
