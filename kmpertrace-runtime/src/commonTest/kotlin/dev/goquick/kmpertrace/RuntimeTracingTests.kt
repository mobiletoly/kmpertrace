package dev.goquick.kmpertrace

import dev.goquick.kmpertrace.core.EventKind
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.core.TraceContext
import dev.goquick.kmpertrace.log.Log
import dev.goquick.kmpertrace.log.LogBackend
import dev.goquick.kmpertrace.log.LoggerConfig
import dev.goquick.kmpertrace.trace.traceSpan
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class CollectingBackend : LogBackend {
    val events = mutableListOf<dev.goquick.kmpertrace.core.LogEvent>()
    override fun log(event: dev.goquick.kmpertrace.core.LogEvent) {
        events += event
    }
}

class RuntimeTracingTests {

    private val backend = CollectingBackend()

    @AfterTest
    fun resetLoggerConfig() {
        LoggerConfig.minLevel = Level.DEBUG
        LoggerConfig.serviceName = null
        LoggerConfig.environment = null
        LoggerConfig.backends = emptyList()
        LoggerConfig.filter = { event -> event.level.ordinal >= LoggerConfig.minLevel.ordinal }
        backend.events.clear()
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
        LoggerConfig.backends = listOf(backend)

        traceSpan("span-one") {
            Log.d { "hello" }
        }

        val logEvent = backend.events.firstOrNull { it.eventKind == EventKind.LOG }
        assertNotNull(logEvent)
        assertEquals("span-one", logEvent.spanName)
        assertNotNull(logEvent.traceId)
        assertNotNull(logEvent.spanId)
    }

    @Test
    fun span_start_and_end_emitted() = runTest {
        LoggerConfig.backends = listOf(backend)

        traceSpan("measure") { /* no-op */ }

        val kinds = backend.events.map { it.eventKind }
        assertTrue(EventKind.SPAN_START in kinds, "SPAN_START not emitted")
        assertTrue(EventKind.SPAN_END in kinds, "SPAN_END not emitted")

        val spanEnd = backend.events.first { it.eventKind == EventKind.SPAN_END }
        assertTrue((spanEnd.durationMs ?: -1) >= 0, "durationMs should be non-negative")
    }

    @Test
    fun span_end_marks_error_when_exception() = runTest {
        LoggerConfig.backends = listOf(backend)

        runCatching {
            traceSpan("boom") {
                error("boom!")
            }
        }

        val spanEnd = backend.events.first { it.eventKind == EventKind.SPAN_END }
        assertEquals("ERROR", spanEnd.attributes["status"])
        assertEquals("IllegalStateException", spanEnd.attributes["error_type"])
        assertEquals("boom!", spanEnd.attributes["error_message"])
        assertNotNull(spanEnd.throwable)
    }

    @Test
    fun span_end_carries_custom_attributes() = runTest {
        LoggerConfig.backends = listOf(backend)

        traceSpan(name = "attrs", attributes = mapOf("user" to "123", "req" to "r1")) {
            // no-op
        }

        val spanEnd = backend.events.first { it.eventKind == EventKind.SPAN_END }
        assertEquals("123", spanEnd.attributes["user"])
        assertEquals("r1", spanEnd.attributes["req"])
    }

    @Test
    fun disabled_debug_log_does_not_evaluate_message() = runTest {
        LoggerConfig.minLevel = Level.INFO
        LoggerConfig.backends = listOf(backend)

        var invoked = false
        Log.d {
            invoked = true
            "should not run"
        }

        assertFalse(invoked)
        assertTrue(backend.events.isEmpty())
    }

    @Test
    fun component_logger_sets_source_metadata() = runTest {
        LoggerConfig.backends = listOf(backend)
        val log = Log.forComponent("ProfileRepo").withOperation("loadProfile")

        log.i { "hello" }

        val event = backend.events.single()
        assertEquals("ProfileRepo", event.sourceComponent)
        assertEquals("loadProfile", event.sourceOperation)
        assertEquals("ProfileRepo.loadProfile", event.sourceLocationHint)
    }

    @Test
    fun trace_span_with_component_propagates_source() = runTest {
        LoggerConfig.backends = listOf(backend)

        traceSpan(component = "ProfileRepo", operation = "refresh") {
            Log.i { "inside span" }
        }

        val start = backend.events.first { it.eventKind == EventKind.SPAN_START }
        val logEvent = backend.events.first { it.eventKind == EventKind.LOG }
        assertEquals("ProfileRepo", start.sourceComponent)
        assertEquals("refresh", start.sourceOperation)
        assertEquals("ProfileRepo", logEvent.sourceComponent)
        assertEquals("refresh", logEvent.sourceOperation)
    }

    @Test
    fun nested_span_inherits_component_and_operation() = runTest {
        LoggerConfig.backends = listOf(backend)

        traceSpan(component = "Downloader", operation = "DownloadProfile") {
            traceSpan("FetchHttp") {
                Log.i { "inside child" }
            }
        }

        val childStart = backend.events.first { it.eventKind == EventKind.SPAN_START && it.spanName == "FetchHttp" }
        val childLog = backend.events.first { it.eventKind == EventKind.LOG && it.message.contains("inside child") }

        assertEquals("Downloader", childStart.sourceComponent)
        assertEquals("DownloadProfile", childStart.sourceOperation)
        assertEquals("Downloader", childLog.sourceComponent)
        assertEquals("DownloadProfile", childLog.sourceOperation)
    }
}
