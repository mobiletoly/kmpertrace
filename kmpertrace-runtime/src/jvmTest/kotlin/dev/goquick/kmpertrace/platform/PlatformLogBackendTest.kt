package dev.goquick.kmpertrace.platform

import dev.goquick.kmpertrace.core.LogRecordKind
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.core.StructuredLogRecord
import dev.goquick.kmpertrace.log.Log
import dev.goquick.kmpertrace.log.LogRecord
import dev.goquick.kmpertrace.log.LogSink
import dev.goquick.kmpertrace.log.KmperTrace
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import dev.goquick.kmpertrace.testutil.parseStructuredSuffix

class PlatformLogBackendTest {

    private val sink = CollectingSink()

    @AfterTest
    fun resetConfig() {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = emptyList())
        sink.records.clear()
    }

    @Test
    fun formatLogLine_produces_logfmt_suffix() {
        val record = sampleEvent()

        val rendered = formatLogLine(record)

        assertTrue(rendered.startsWith("2025-01-02T03:04:05Z INFO TestLogger hello world |{ ts=2025-01-02T03:04:05Z"))
        assertTrue(rendered.contains("""trace=trace1 span=span1 kind=SPAN_START"""))
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
        val record = sampleEvent()
        val buffer = java.io.ByteArrayOutputStream()
        val previousOut = System.out
        try {
            System.setOut(java.io.PrintStream(buffer, true, Charsets.UTF_8))
            val rendered = renderLogLine(record)
            val record = LogRecord(
                timestamp = record.timestamp,
                level = record.level,
                tag = record.loggerName,
                message = rendered.humanMessage,
                line = rendered.line,
                structuredSuffix = rendered.structuredSuffix
            )
            PlatformLogSink.emit(record)
        } finally {
            System.setOut(previousOut)
        }

        val printed = buffer.toString(Charsets.UTF_8).trimEnd()
        assertEquals(formatLogLine(record), printed)
    }

    @Test
    fun non_traced_log_uses_zero_ids() {
        val record = StructuredLogRecord(
            timestamp = Instant.parse("2025-01-02T03:04:05Z"),
            level = Level.INFO,
            loggerName = "NoTrace",
            message = "plain"
        )

        val rendered = formatLogLine(record)
        assertTrue(rendered.startsWith("2025-01-02T03:04:05Z INFO NoTrace plain |{ ts=2025-01-02T03:04:05Z"))
        assertTrue(rendered.contains("""head="plain""""))
        assertTrue(rendered.contains("log=NoTrace"))
    }

    @Test
    fun head_is_capped_to_15_chars() {
        val record = sampleEvent().copy(message = "1234567890abcdefg")
        val rendered = formatLogLine(record)
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
        KmperTrace.configure(sinks = listOf(sink))
        val current = Thread.currentThread().name

        Log.i(tag = "ThreadTest") { "message" }

        val record = sink.records.single()
        val threadName = parseStructuredSuffix(record.structuredSuffix)["thread"]
        assertNotNull(threadName)
        assertEquals(current, threadName)
    }

    private fun sampleEvent(): StructuredLogRecord = StructuredLogRecord(
        timestamp = Instant.parse("2025-01-02T03:04:05Z"),
        level = Level.INFO,
        loggerName = "TestLogger",
        message = "hello world",
        traceId = "trace1",
        spanId = "span1",
        parentSpanId = null,
        logRecordKind = LogRecordKind.SPAN_START,
        spanName = "op",
        durationMs = 123,
        threadName = "main-thread",
        serviceName = "svc",
        environment = "dev",
        attributes = mapOf("k" to "v", "foo" to "bar baz"),
        throwable = IllegalStateException("boom!")
    )

    private class CollectingSink : LogSink {
        val records = mutableListOf<LogRecord>()
        override fun emit(record: LogRecord) {
            records += record
        }
    }
}
