package dev.goquick.kmpertrace

import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.core.TraceContext
import dev.goquick.kmpertrace.log.Log
import dev.goquick.kmpertrace.log.LogRecord
import dev.goquick.kmpertrace.log.LogSink
import dev.goquick.kmpertrace.log.KmperTrace
import dev.goquick.kmpertrace.log.inlineSpan
import dev.goquick.kmpertrace.log.journey
import dev.goquick.kmpertrace.log.span
import dev.goquick.kmpertrace.trace.captureTraceSnapshot
import dev.goquick.kmpertrace.trace.TraceTrigger
import dev.goquick.kmpertrace.trace.traceSpan
import dev.goquick.kmpertrace.trace.withCurrentTrace
import dev.goquick.kmpertrace.trace.withTrace
import dev.goquick.kmpertrace.testutil.parseStructuredSuffix
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class CollectingSink : LogSink {
    val records = mutableListOf<LogRecord>()
    override fun emit(record: LogRecord) {
        records += record
    }
}

class RuntimeTracingTests {

    private val sink = CollectingSink()
    private val utcZTimestampRegex =
        Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$""")

    @AfterTest
    fun resetLoggerConfig() {
        KmperTrace.configure(
            minLevel = Level.DEBUG,
            serviceName = null,
            environment = null,
            sinks = emptyList(),
            filter = { true },
            renderGlyphs = true,
            emitDebugAttributes = false
        )
        sink.records.clear()
    }

    @Test
    fun traceContext_propagates_and_nests() = runTest {
        lateinit var root: TraceContext
        lateinit var child: TraceContext

        traceSpan("root") {
            root = traceContext
            traceSpan("child") {
                child = traceContext
            }
        }

        assertEquals(root.traceId, child.traceId)
        assertEquals(root.spanId, child.parentSpanId)
    }

    @Test
    fun log_in_span_has_trace_fields() = runTest {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))

        traceSpan("span-one") {
            Log.d { "hello" }
        }

        val record = sink.records.firstOrNull { it.message == "hello" }
        assertNotNull(record)
        val fields = parseStructuredSuffix(record.structuredSuffix)
        assertNotNull(fields["trace"])
        assertNotNull(fields["span"])
    }

    @Test
    fun timestamp_is_emitted_as_utc_z() = runTest {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))

        Log.i { "hello" }

        val record = sink.records.single()
        val fields = parseStructuredSuffix(record.structuredSuffix)
        val ts = fields["ts"] ?: error("ts missing from structured suffix: ${record.structuredSuffix}")

        assertTrue(
            utcZTimestampRegex.matches(ts),
            "expected ts to be canonical UTC Z timestamp, got: $ts"
        )
    }

    @Test
    fun span_start_and_end_emitted() = runTest {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))

        traceSpan("measure") { /* no-op */ }

        val fields = sink.records.map { parseStructuredSuffix(it.structuredSuffix) }
        val kinds = fields.mapNotNull { it["kind"] }.toSet()
        assertTrue("SPAN_START" in kinds, "SPAN_START not emitted")
        assertTrue("SPAN_END" in kinds, "SPAN_END not emitted")
    }

    @Test
    fun span_end_marks_error_when_exception() = runTest {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))

        runCatching {
            traceSpan("boom") {
                error("boom!")
            }
        }

        val spanEnd = sink.records
            .map { parseStructuredSuffix(it.structuredSuffix) }
            .first { it["kind"] == "SPAN_END" }

        assertEquals("ERROR", spanEnd["status"])
        assertEquals("IllegalStateException", spanEnd["err_type"])
        assertEquals("boom!", spanEnd["err_msg"])
        assertNotNull(spanEnd["stack_trace"])
    }

    @Test
    fun span_end_carries_custom_attributes() = runTest {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))

        traceSpan(name = "attrs", attributes = mapOf("user" to "123", "req" to "r1")) {
            // no-op
        }

        val spanEnd = sink.records
            .map { parseStructuredSuffix(it.structuredSuffix) }
            .first { it["kind"] == "SPAN_END" }
        assertEquals("123", spanEnd["a:user"])
        assertEquals("r1", spanEnd["a:req"])
    }

    @Test
    fun journey_emits_start_milestone_and_trigger_attribute() = runTest {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))

        val log = Log.forComponent("UI")
        log.journey(
            operation = "pairDevice",
            trigger = TraceTrigger.tap("PairButton"),
            attributes = mapOf("rescan" to "false")
        ) {
            Log.d { "inside" }
        }

        val milestone = sink.records.firstOrNull { it.message.startsWith("journey started") }
        assertNotNull(milestone, "expected journey to emit an INFO milestone log")
        assertTrue(milestone.message.contains("tap.PairButton"), "expected trigger to be visible in milestone: ${milestone.message}")

        val spanEnd = sink.records
            .map { parseStructuredSuffix(it.structuredSuffix) }
            .first { it["kind"] == "SPAN_END" }
        assertEquals("tap.PairButton", spanEnd["a:trigger"])
        assertEquals("false", spanEnd["a:rescan"])
    }

    @Test
    fun journey_nests_in_active_trace_by_default() = runTest {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))

        val log = Log.forComponent("UI")
        lateinit var root: TraceContext

        traceSpan("root") {
            root = traceContext
            log.journey(operation = "childJourney", trigger = TraceTrigger.tap("PairButton")) {
                Log.d { "inside journey" }
            }
        }

        val fields = sink.records.map { parseStructuredSuffix(it.structuredSuffix) }
        val journeySpanStart = fields.first { it["kind"] == "SPAN_START" && it["name"] == "UI.childJourney" }
        assertEquals(root.traceId, journeySpanStart["trace"])
        assertEquals(root.spanId, journeySpanStart["parent"])
    }

    @Test
    fun disabled_debug_log_does_not_evaluate_message() = runTest {
        KmperTrace.configure(minLevel = Level.INFO, sinks = listOf(sink))

        var invoked = false
        Log.d {
            invoked = true
            "should not run"
        }

        assertFalse(invoked)
        assertTrue(sink.records.isEmpty())
    }

    @Test
    fun component_logger_sets_source_metadata() = runTest {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))
        val log = Log.forComponent("ProfileRepo").withOperation("loadProfile")

        log.i { "hello" }

        val record = sink.records.single()
        val fields = parseStructuredSuffix(record.structuredSuffix)
        assertEquals("ProfileRepo/loadProfile", fields["src"])
    }

    @Test
    fun trace_span_with_component_propagates_source() = runTest {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))

        traceSpan(component = "ProfileRepo", operation = "refresh") {
            Log.i { "inside span" }
        }

        val fields = sink.records.map { parseStructuredSuffix(it.structuredSuffix) }
        val spanStart = fields.first { it["kind"] == "SPAN_START" }
        val log = fields.first { it["kind"] == null }
        assertEquals("ProfileRepo/refresh", spanStart["src"])
        assertEquals("ProfileRepo/refresh", log["src"])
    }

    @Test
    fun component_log_context_inside_span_inherits_span_location_hint() = runTest {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))
        val log = Log.forComponent("ProfileRepo")

        traceSpan(component = "ProfileRepo", operation = "refresh") {
            log.i { "inside span" }
        }

        val record = sink.records.first { it.message == "inside span" }
        val fields = parseStructuredSuffix(record.structuredSuffix)
        assertEquals("ProfileRepo/refresh", fields["src"])
    }

    @Test
    fun log_context_span_passes_attributes_to_span_end() = runTest {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))

        val log = Log.forComponent("ProfileRepo")
        log.span(operation = "refresh", attributes = mapOf("user" to "123")) {
            // no-op
        }

        val spanEnd = sink.records
            .map { parseStructuredSuffix(it.structuredSuffix) }
            .first { it["kind"] == "SPAN_END" }
        assertEquals("123", spanEnd["a:user"])
    }

    @Test
    fun log_context_inline_span_passes_attributes_to_span_end() = runTest {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))

        val log = Log.forComponent("ProfileRepo")
        log.inlineSpan(operation = "syncWork", attributes = mapOf("req" to "r1")) {
            // no-op
        }

        val spanEnd = sink.records
            .map { parseStructuredSuffix(it.structuredSuffix) }
            .first { it["kind"] == "SPAN_END" }
        assertEquals("r1", spanEnd["a:req"])
    }

    @Test
    fun span_drops_debug_attributes_by_default() = runTest {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink), emitDebugAttributes = false)

        traceSpan(
            name = "attrs",
            attributes = mapOf("x" to "1", "?userEmail" to "a@b.com")
        ) {
            // no-op
        }

        val spanEnd = sink.records
            .map { parseStructuredSuffix(it.structuredSuffix) }
            .first { it["kind"] == "SPAN_END" }
        assertEquals("1", spanEnd["a:x"])
        assertEquals(null, spanEnd["d:userEmail"])
    }

    @Test
    fun span_includes_debug_attributes_when_enabled() = runTest {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink), emitDebugAttributes = true)

        traceSpan(
            name = "attrs",
            attributes = mapOf("?userEmail" to "a@b.com")
        ) {
            // no-op
        }

        val spanEnd = sink.records
            .map { parseStructuredSuffix(it.structuredSuffix) }
            .first { it["kind"] == "SPAN_END" }
        assertEquals("a@b.com", spanEnd["d:userEmail"])
    }

    @Test
    fun span_sanitizes_invalid_attribute_keys() = runTest {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink), emitDebugAttributes = true)

        traceSpan(
            name = "attrs",
            attributes = mapOf(
                "user id" to "1",
                "?bad key!" to "2"
            )
        ) {
            // no-op
        }

        val spanEnd = sink.records
            .map { parseStructuredSuffix(it.structuredSuffix) }
            .first { it["kind"] == "SPAN_END" }
        assertEquals("1", spanEnd["a:invalid_user_id"])
        assertEquals("2", spanEnd["d:invalid_bad_key_"])
    }

    @Test
    fun nested_span_inherits_component_and_operation() = runTest {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))

        traceSpan(component = "Downloader", operation = "DownloadProfile") {
            traceSpan("FetchHttp") {
                Log.i { "inside child" }
            }
        }

        val fields = sink.records.map { parseStructuredSuffix(it.structuredSuffix) }
        val childStart = fields.first { it["kind"] == "SPAN_START" && it["name"] == "FetchHttp" }
        val childLog = fields.first { it["kind"] == null && it["head"] == "inside child" }
        assertEquals("Downloader/DownloadProfile", childStart["src"])
        assertEquals("Downloader/DownloadProfile", childLog["src"])
    }

    @Test
    fun withCurrentTrace_preserves_binding_after_dispatcher_hop() = runTest {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))
        val hopDispatcher = StandardTestDispatcher(testScheduler)

        traceSpan(component = "Repo", operation = "save") {
            withContext(hopDispatcher) {
                withCurrentTrace {
                    Log.i { "hop-log" }
                }
            }
        }

        val record = sink.records.first { it.message == "hop-log" }
        val fields = parseStructuredSuffix(record.structuredSuffix)
        assertNotNull(fields["trace"])
        assertNotNull(fields["span"])
    }

    @Test
    fun traceSnapshot_withTrace_preserves_binding_in_suspending_block() = runTest {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))
        val hopDispatcher = StandardTestDispatcher(testScheduler)

        traceSpan(component = "Repo", operation = "save") {
            val snapshot = captureTraceSnapshot()
            withContext(hopDispatcher) {
                snapshot.withTrace {
                    Log.i { "snapshot-log" }
                }
            }
        }

        val record = sink.records.first { it.message == "snapshot-log" }
        val fields = parseStructuredSuffix(record.structuredSuffix)
        assertNotNull(fields["trace"])
        assertNotNull(fields["span"])
    }
}
