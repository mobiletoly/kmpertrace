package dev.goquick.kmpertrace.trace

import dev.goquick.kmpertrace.core.EventKind
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.log.Log
import dev.goquick.kmpertrace.log.LoggerConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LoggingBindingIsolationTest {
    private val backend = AndroidCollectingBackend()

    @AfterTest
    fun tearDown() {
        LoggerConfig.backends = emptyList()
        LoggerConfig.minLevel = Level.DEBUG
        LoggerConfig.filter = { event -> event.level.ordinal >= LoggerConfig.minLevel.ordinal }
        backend.events.clear()
    }

    @Test
    fun unbound_logs_after_span_are_not_attached() = runBlocking {
        LoggerConfig.backends = listOf(backend)

        traceSpan("isolation-span") {
            Log.d { "span-log" }
        }

        Log.d { "outside-span-log" }

        val spanLog = backend.events.first { it.eventKind == EventKind.LOG && it.message == "span-log" }
        val outsideLog = backend.events.first { it.eventKind == EventKind.LOG && it.message == "outside-span-log" }

        assertNotNull(spanLog.traceId)
        assertNotNull(spanLog.spanId)
        assertNull(outsideLog.traceId, "outside log should not carry span trace id")
        assertNull(outsideLog.spanId, "outside log should not carry span id")
        assertNull(outsideLog.parentSpanId, "outside log should not carry parent span id")
    }

    @Test
    fun parallel_spans_keep_trace_ids_separate() = runBlocking {
        LoggerConfig.backends = listOf(backend)

        coroutineScope {
            val a = async {
                traceSpan("span-A") { Log.d { "from-A" } }
            }
            val b = async {
                traceSpan("span-B") { Log.d { "from-B" } }
            }
            a.await()
            b.await()
        }

        val logA = backend.events.first { it.eventKind == EventKind.LOG && it.message == "from-A" }
        val logB = backend.events.first { it.eventKind == EventKind.LOG && it.message == "from-B" }

        assertNotNull(logA.traceId)
        assertNotNull(logB.traceId)
        assertNotEquals(logA.traceId, logB.traceId, "separate spans should have different trace ids")
        assertEquals("span-A", logA.spanName)
        assertEquals("span-B", logB.spanName)
    }
}
