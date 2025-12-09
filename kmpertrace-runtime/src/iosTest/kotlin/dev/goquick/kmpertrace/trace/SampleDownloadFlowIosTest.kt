package dev.goquick.kmpertrace.trace

import dev.goquick.kmpertrace.core.EventKind
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.log.Log
import dev.goquick.kmpertrace.log.LoggerConfig
import dev.goquick.kmpertrace.log.LogBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

private class SampleCollectingBackend : LogBackend {
    val events = mutableListOf<dev.goquick.kmpertrace.core.LogEvent>()
    override fun log(event: dev.goquick.kmpertrace.core.LogEvent) {
        events += event
    }
}

/**
 * Mirrors the FakeDownloader + ProfileViewModel flow to reproduce iOS binding issues.
 */
class SampleDownloadFlowIosTest {
    private val backend = SampleCollectingBackend()

    @AfterTest
    fun tearDown() {
        LoggerConfig.backends = emptyList()
        LoggerConfig.minLevel = Level.DEBUG
        LoggerConfig.filter = { event -> event.level.ordinal >= LoggerConfig.minLevel.ordinal }
        backend.events.clear()
    }

    @Test
    fun download_spans_bind_logs_and_differentiate_traces() = runBlocking {
        LoggerConfig.backends = listOf(backend)
        val downloader = TestDownloader()

        withTimeout(5_000) {
            val bJobRef = AtomicReference<Job?>(null)
            val a = launch(Dispatchers.Default) {
                runCatching {
                    downloader.download(label = "DownloadA", totalChunks = 3, failAtPercent = 66) { percent ->
                        if (percent >= 33 && bJobRef.value == null) {
                            val b = launch(Dispatchers.Default) { downloader.download(label = "DownloadB", totalChunks = 3, failAtPercent = null) }
                            bJobRef.compareAndSet(null, b)
                        }
                    }
                }
            }
            val bJob = bJobRef.value
            if (bJob != null) {
                joinAll(a, bJob)
            } else {
                a.join()
            }
        }

        // Collect per-root-span events.
        val downloads = backend.events.filter { it.spanName?.startsWith("Downloader.") == true }
        val aTraceId = downloads.first { it.spanName == "Downloader.DownloadA" }.traceId
        val bTraceId = downloads.first { it.spanName == "Downloader.DownloadB" }.traceId
        assertNotNull(aTraceId, "DownloadA trace id missing")
        assertNotNull(bTraceId, "DownloadB trace id missing")
        assertNotEquals(aTraceId, bTraceId, "Separate downloads should use different traces")

        val downloaderLogs = backend.events.filter { it.loggerName == "Downloader" && it.eventKind == EventKind.LOG }
        assertNotEquals(0, downloaderLogs.size, "No downloader logs recorded")
        val downloaderLogsMissingContext = downloaderLogs.filter { it.traceId == null || it.spanId == null || it.spanName == null }
        assertEquals(
            emptyList(),
            downloaderLogsMissingContext,
            "Downloader logs lost trace/span binding: $downloaderLogsMissingContext"
        )

        // All log events inside DownloadA or its storeChunk children should carry a trace.
        val aLogsWithoutTrace = downloads.filter { it.eventKind == EventKind.LOG && it.spanName?.contains("DownloadA") == true }
            .filter { it.traceId == null || it.spanId == null }
        assertEquals(0, aLogsWithoutTrace.size, "DownloadA logs lost trace/span binding: $aLogsWithoutTrace")

        // Same check for DownloadB.
        val bLogsWithoutTrace = downloads.filter { it.eventKind == EventKind.LOG && it.spanName?.contains("DownloadB") == true }
            .filter { it.traceId == null || it.spanId == null }
        assertEquals(0, bLogsWithoutTrace.size, "DownloadB logs lost trace/span binding: $bLogsWithoutTrace")
    }
}

private class TestDownloader {
    private val log = Log.forComponent("Downloader")

    suspend fun download(label: String, totalChunks: Int, failAtPercent: Int?, onProgress: suspend (Int) -> Unit = {}) {
        val jobId = label.hashCode().toLong().toString()
        traceSpan(component = "Downloader", operation = label) {
            // Hop to Default to mimic disk/network dispatchers.
            withTraceContext(Dispatchers.Default) {
                assertTraceBound()
                log.withOperation(label).i { "Download $label starting (jobId=$jobId)" }
                repeat(totalChunks) { idx ->
                    delay(5)
                    val percent = ((idx + 1) * 100) / totalChunks
                    assertTraceBound()
                    log.withOperation(label).d { "Download $label progress $percent% (jobId=$jobId)" }
                    onProgress(percent)
                    if (failAtPercent != null && percent >= failAtPercent) {
                        assertTraceBound()
                        log.withOperation(label).e { "Download $label failed (jobId=$jobId)" }
                        throw IllegalStateException("Fatal network error on \"$label\" at $percent% (jobId=$jobId)")
                    }
                    storeChunk(jobId, label, idx, totalChunks)
                }
                assertTraceBound()
                log.withOperation(label).i { "Download $label finished (jobId=$jobId)" }
            }
        }
    }

    private suspend fun storeChunk(jobId: String, label: String, idx: Int, total: Int) {
        traceSpan(component = "Downloader", operation = "storeChunk") {
            withTraceContext(Dispatchers.Default) {
                assertTraceBound()
                chunkValidator()
                assertTraceBound()
                log.withOperation("storeChunk").d { "Storing chunk ${idx + 1}/$total for $label (jobId=$jobId)" }
                delay(5)
                assertTraceBound()
                log.withOperation("storeChunk").d { "Chunk ${idx + 1}/$total stored for $label" }
            }
        }
    }
}

private fun assertTraceBound() {
    checkNotNull(TraceContextStorage.get()) { "trace context missing on thread" }
}

private suspend fun chunkValidator() {
    Log.d { "Validated chunk successfully" }
    delay(2)
}
