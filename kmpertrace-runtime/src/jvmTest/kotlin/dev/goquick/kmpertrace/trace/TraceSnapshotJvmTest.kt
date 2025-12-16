package dev.goquick.kmpertrace.trace

import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.log.Log
import dev.goquick.kmpertrace.log.LogRecord
import dev.goquick.kmpertrace.log.LogSink
import dev.goquick.kmpertrace.log.KmperTrace
import dev.goquick.kmpertrace.testutil.parseStructuredSuffix
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

private class CollectingSink : LogSink {
    val records = mutableListOf<LogRecord>()
    override fun emit(record: LogRecord) {
        records += record
    }
}

class TraceSnapshotJvmTest {
    private val sink = CollectingSink()

    @AfterTest
    fun tearDown() {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = emptyList())
        sink.records.clear()
    }

    @Test
    fun executor_callbacks_can_bind_to_origin_span_and_restore_thread_state() = runBlocking {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))

        val executor = Executors.newSingleThreadExecutor()
        try {
            traceSpan(component = "Snapshot", operation = "Root") {
                val snapshot = captureTraceSnapshot()

                val latch = CountDownLatch(3)
                executor.executeWithTrace(snapshot) {
                    Log.d { "bound-executor" }
                    latch.countDown()
                }

                executor.execute(
                    Runnable {
                        Log.d { "bound-runnable" }
                        latch.countDown()
                    }.withTrace(snapshot)
                )

                executor.execute(
                    Runnable {
                        Log.d { "unbound" }
                        latch.countDown()
                    }
                )

                withTimeout(5_000) {
                    while (!latch.await(10, TimeUnit.MILLISECONDS)) Unit
                }
            }
        } finally {
            executor.shutdownNow()
        }

        val spanStartFields =
            sink.records
                .map { parseStructuredSuffix(it.structuredSuffix) }
                .first { it["kind"] == "SPAN_START" && it["name"] == "Snapshot.Root" }

        val traceId = spanStartFields["trace"]
        val spanId = spanStartFields["span"]
        assertNotNull(traceId, "SPAN_START missing trace id")
        assertNotNull(spanId, "SPAN_START missing span id")

        val boundExecutorFields = parseStructuredSuffix(sink.records.first { it.message == "bound-executor" }.structuredSuffix)
        assertEquals(traceId, boundExecutorFields["trace"])
        assertEquals(spanId, boundExecutorFields["span"])

        val boundRunnableFields = parseStructuredSuffix(sink.records.first { it.message == "bound-runnable" }.structuredSuffix)
        assertEquals(traceId, boundRunnableFields["trace"])
        assertEquals(spanId, boundRunnableFields["span"])

        val unboundFields = parseStructuredSuffix(sink.records.first { it.message == "unbound" }.structuredSuffix)
        assertNull(unboundFields["trace"], "unbound log should not carry trace id")
        assertNull(unboundFields["span"], "unbound log should not carry span id")
        assertNull(unboundFields["parent"], "unbound log should not carry parent span id")
    }
}
