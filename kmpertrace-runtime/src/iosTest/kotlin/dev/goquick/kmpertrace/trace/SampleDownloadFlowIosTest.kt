package dev.goquick.kmpertrace.trace

import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.log.Log
import dev.goquick.kmpertrace.log.LoggerConfig
import dev.goquick.kmpertrace.log.LogRecord
import dev.goquick.kmpertrace.log.LogSink
import dev.goquick.kmpertrace.log.KmperTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import dev.goquick.kmpertrace.testutil.parseStructuredSuffix

private class SampleCollectingSink : LogSink {
    val records = mutableListOf<LogRecord>()
    override fun emit(record: LogRecord) {
        records += record
    }
}

/**
 * Mirrors the FakeDownloader + ProfileViewModel flow to reproduce iOS binding issues.
 */
class SampleDownloadFlowIosTest {
    private val sink = SampleCollectingSink()

    @AfterTest
    fun tearDown() {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = emptyList())
        sink.records.clear()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun download_spans_bind_logs_and_differentiate_traces() = runBlocking {
        KmperTrace.configure(minLevel = Level.DEBUG, sinks = listOf(sink))
        val downloader = TestDownloader()

        withTimeout(5_000) {
            // Run two downloads concurrently (A may fail, B should succeed) to verify trace isolation.
            val dispatcher = Dispatchers.Default.limitedParallelism(2)
            coroutineScope {
                val a = async(dispatcher) {
                    runCatching { downloader.download(label = "DownloadA", totalChunks = 3, failAtPercent = 66) }
                }
                val b = async(dispatcher) {
                    downloader.download(label = "DownloadB", totalChunks = 3, failAtPercent = null)
                }
                a.await()
                b.await()
            }
        }

        val fields = sink.records.map { parseStructuredSuffix(it.structuredSuffix) }
        val aTraceId = fields.first { it["kind"] == "SPAN_START" && it["name"] == "Downloader.DownloadA" }["trace"]
        val bTraceId = fields.first { it["kind"] == "SPAN_START" && it["name"] == "Downloader.DownloadB" }["trace"]
        assertNotNull(aTraceId, "DownloadA trace id missing")
        assertNotNull(bTraceId, "DownloadB trace id missing")
        assertNotEquals(aTraceId, bTraceId, "Separate downloads should use different traces")

        val downloaderLogs = sink.records.filter { it.tag == "Downloader" }
        assertNotEquals(0, downloaderLogs.size, "No downloader logs recorded")
        val downloaderLogsMissingContext = downloaderLogs.filter {
            val f = parseStructuredSuffix(it.structuredSuffix)
            f["trace"] == null || f["span"] == null
        }
        assertEquals(
            emptyList(),
            downloaderLogsMissingContext,
            "Downloader logs lost trace/span binding: $downloaderLogsMissingContext"
        )

        val aLogsWithoutTrace = sink.records
            .filter { parseStructuredSuffix(it.structuredSuffix)["src"] == "Downloader/DownloadA" }
            .filter { r ->
                val f = parseStructuredSuffix(r.structuredSuffix)
                f["trace"] == null || f["span"] == null
            }
        assertEquals(0, aLogsWithoutTrace.size, "DownloadA logs lost trace/span binding: $aLogsWithoutTrace")

        val bLogsWithoutTrace = sink.records
            .filter { parseStructuredSuffix(it.structuredSuffix)["src"] == "Downloader/DownloadB" }
            .filter { r ->
                val f = parseStructuredSuffix(r.structuredSuffix)
                f["trace"] == null || f["span"] == null
            }
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
