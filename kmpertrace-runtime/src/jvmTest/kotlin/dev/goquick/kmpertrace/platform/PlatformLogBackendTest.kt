package dev.goquick.kmpertrace.platform

import dev.goquick.kmpertrace.core.EventKind
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.core.LogEvent
import dev.goquick.kmpertrace.log.Log
import dev.goquick.kmpertrace.log.LogBackend
import dev.goquick.kmpertrace.log.LoggerConfig
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformLogBackendTest {

    private val backend = CollectingBackend()

    @AfterTest
    fun resetConfig() {
        LoggerConfig.backends = emptyList()
        LoggerConfig.minLevel = Level.DEBUG
        backend.events.clear()
    }

    @Test
    fun formatLogLine_produces_logfmt_suffix() {
        val event = sampleEvent()

        val rendered = formatLogLine(event)

        assertTrue(rendered.startsWith("2025-01-02T03:04:05Z INFO TestLogger hello world |{ ts=2025-01-02T03:04:05Z"))
        assertTrue(rendered.contains("""trace=trace1 span=span1 ev=SPAN_START"""))
        assertTrue(rendered.contains("""name="op" head="hello world""""))
        assertTrue(rendered.contains("""log=TestLogger"""))
        assertTrue(rendered.contains("""thread="main-thread""""))
        assertTrue(rendered.contains("""svc=svc"""))
        assertTrue(rendered.contains("""env=dev"""))
        assertTrue(rendered.contains("""k="v" foo="bar baz""""))
        assertTrue(rendered.contains("""throwable="IllegalStateException""""))
        assertTrue(rendered.contains("""stack_trace=""""))
        assertTrue(rendered.contains("""IllegalStateException: boom!"""))
    }

    @Test
    fun platform_backend_prints_formatted_line() {
        val event = sampleEvent()
        val buffer = java.io.ByteArrayOutputStream()
        val previousOut = System.out
        try {
            System.setOut(java.io.PrintStream(buffer, true, Charsets.UTF_8))
            PlatformLogBackend.log(event)
        } finally {
            System.setOut(previousOut)
        }

        val printed = buffer.toString(Charsets.UTF_8).trimEnd()
        assertEquals(formatLogLine(event), printed)
    }

    @Test
    fun non_traced_log_uses_zero_ids() {
        val event = LogEvent(
            timestamp = Instant.parse("2025-01-02T03:04:05Z"),
            level = Level.INFO,
            loggerName = "NoTrace",
            message = "plain"
        )

        val rendered = formatLogLine(event)
        assertTrue(rendered.startsWith("2025-01-02T03:04:05Z INFO NoTrace plain |{ ts=2025-01-02T03:04:05Z"))
        assertTrue(rendered.contains("""head="plain""""))
        assertTrue(rendered.contains("log=NoTrace"))
    }

    @Test
    fun head_is_capped_to_15_chars() {
        val event = sampleEvent().copy(message = "1234567890abcdefg")
        val rendered = formatLogLine(event)
        assertTrue(rendered.contains("""head="1234567890abcde""""))
    }

    @Test
    fun structured_suffix_has_single_pipe_separator() {
        val rendered = formatLogLine(sampleEvent())
        // Only the structured wrapper should introduce "|{"
        assertEquals(1, rendered.windowed(size = 2, step = 1).count { it == "|{" })
        assertTrue(!rendered.contains(" | |{"), "double pipe separator should not appear: $rendered")
    }

    @Test
    fun log_captures_thread_name() {
        LoggerConfig.backends = listOf(backend)
        val current = Thread.currentThread().name

        Log.i(tag = "ThreadTest") { "message" }

        val event = backend.events.single()
        assertNotNull(event.threadName)
        assertEquals(current, event.threadName)
    }

    private fun sampleEvent(): LogEvent = LogEvent(
        timestamp = Instant.parse("2025-01-02T03:04:05Z"),
        level = Level.INFO,
        loggerName = "TestLogger",
        message = "hello world",
        traceId = "trace1",
        spanId = "span1",
        parentSpanId = null,
        eventKind = EventKind.SPAN_START,
        spanName = "op",
        durationMs = 123,
        threadName = "main-thread",
        serviceName = "svc",
        environment = "dev",
        attributes = mapOf("k" to "v", "foo" to "bar baz"),
        throwable = IllegalStateException("boom!")
    )

    private class CollectingBackend : LogBackend {
        val events = mutableListOf<LogEvent>()
        override fun log(event: LogEvent) {
            events += event
        }
    }
}
