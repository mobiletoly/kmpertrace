package dev.goquick.kmpertrace.trace

import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.log.Log
import dev.goquick.kmpertrace.log.KmperTrace
import dev.goquick.kmpertrace.testutil.parseStructuredSuffix
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentInheritanceAndroidTest {
    private val sink = AndroidCollectingSink()

    @AfterTest
    fun tearDown() {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = emptyList())
        sink.records.clear()
    }

    @Test
    fun nested_span_inherits_component_and_operation() = kotlinx.coroutines.test.runTest {
        KmperTrace.configure(sinks = listOf(sink))

        traceSpan(component = "Downloader", operation = "DownloadProfile") {
            traceSpan("FetchHttp") {
                Log.i { "inside child" }
            }
        }

        val childStart = sink.records
            .map { parseStructuredSuffix(it.structuredSuffix) }
            .first { it["kind"] == "SPAN_START" && it["name"] == "FetchHttp" }
        val childLog = sink.records
            .map { parseStructuredSuffix(it.structuredSuffix) }
            .first { it["kind"] == null && it["head"] == "inside child" }

        assertEquals("Downloader/DownloadProfile", childStart["src"])
        assertEquals("Downloader/DownloadProfile", childLog["src"])
    }
}
