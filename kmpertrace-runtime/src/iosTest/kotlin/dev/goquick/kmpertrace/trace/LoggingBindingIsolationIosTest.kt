package dev.goquick.kmpertrace.trace

import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.log.Log
import dev.goquick.kmpertrace.log.KmperTrace
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import dev.goquick.kmpertrace.testutil.parseStructuredSuffix

class LoggingBindingIsolationIosTest {
    private val sink = IosCollectingSink()

    @AfterTest
    fun tearDown() {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = emptyList())
        sink.records.clear()
    }

    @Test
    fun unbound_logs_after_span_are_not_attached() = runBlocking {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))

        traceSpan("isolation-span") {
            Log.d { "span-log" }
        }

        Log.d { "outside-span-log" }

        val spanLog = sink.records.first { it.message == "span-log" }
        val outsideLog = sink.records.first { it.message == "outside-span-log" }
        val spanFields = parseStructuredSuffix(spanLog.structuredSuffix)
        val outsideFields = parseStructuredSuffix(outsideLog.structuredSuffix)

        assertNotNull(spanFields["trace"])
        assertNotNull(spanFields["span"])
        assertNull(outsideFields["trace"], "outside log should not carry span trace id")
        assertNull(outsideFields["span"], "outside log should not carry span id")
        assertNull(outsideFields["parent"], "outside log should not carry parent span id")
    }

    @Test
    fun parallel_spans_keep_traceIds_separate() = runBlocking {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))

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

        val logA = sink.records.first { it.message == "from-A" }
        val logB = sink.records.first { it.message == "from-B" }
        val fieldsA = parseStructuredSuffix(logA.structuredSuffix)
        val fieldsB = parseStructuredSuffix(logB.structuredSuffix)

        assertNotNull(fieldsA["trace"])
        assertNotNull(fieldsB["trace"])
        assertNotEquals(fieldsA["trace"], fieldsB["trace"], "separate spans should have different trace ids")
    }
}
