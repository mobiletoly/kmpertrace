package dev.goquick.kmpertrace.trace

import dev.goquick.kmpertrace.core.EventKind
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.log.Log
import dev.goquick.kmpertrace.log.LogBackend
import dev.goquick.kmpertrace.log.LoggerConfig
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class CollectingBackend : LogBackend {
    val events = mutableListOf<dev.goquick.kmpertrace.core.LogEvent>()
    override fun log(event: dev.goquick.kmpertrace.core.LogEvent) {
        events += event
    }
}

class ComponentInheritanceWasmTest {
    private val backend = CollectingBackend()

    @AfterTest
    fun tearDown() {
        LoggerConfig.backends = emptyList()
        LoggerConfig.minLevel = Level.DEBUG
        backend.events.clear()
    }

    @Test
    fun nested_span_inherits_component_and_operation() = kotlinx.coroutines.test.runTest {
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
