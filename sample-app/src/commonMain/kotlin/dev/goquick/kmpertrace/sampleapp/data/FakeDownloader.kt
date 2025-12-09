package dev.goquick.kmpertrace.sampleapp.data

import dev.goquick.kmpertrace.log.Log
import dev.goquick.kmpertrace.trace.traceSpan
import kotlinx.coroutines.delay
import kotlin.random.Random

class FakeDownloader {
    private val log = Log.forComponent("Downloader")

    suspend fun download(
        label: String,
        totalChunks: Int = 10,
        chunkDelayMs: Long = 500L,
        onProgress: (Int) -> Unit = {}
    ) {
        val jobId = (Random.nextInt() % 100000).toString()
        val simulateFailure = label == "DownloadA"
        var failureInjected = false
        traceSpan(
            component = "Downloader",
            operation = label,
            attributes = mapOf("jobId" to jobId, "chunks" to totalChunks.toString())
        ) {
            log.withOperation(label).i { "Download $label starting (jobId=$jobId)" }
            repeat(totalChunks) { idx ->
                delay(chunkDelayMs)
                val percent = ((idx + 1) * 100) / totalChunks
                onProgress(percent)
                log.withOperation(label).d { "Download $label progress $percent% (jobId=$jobId)" }
                if (simulateFailure && !failureInjected && percent >= 50) {
                    failureInjected = true
                    val ex = IllegalStateException("Fatal network error on \"$label\" at ${percent}% (jobId=$jobId)")
                    log.withOperation(label).e { "Download $label failed (jobId=$jobId)" }
                    throw ex
                }
                storeChunk(jobId = jobId, label = label, chunkIndex = idx, totalChunks = totalChunks)
            }
            log.withOperation(label).i { "Download $label finished (jobId=$jobId)" }
        }
    }

    private suspend fun storeChunk(jobId: String, label: String, chunkIndex: Int, totalChunks: Int) {
        val bytes = 64 * 1024 // 64KB fake chunk
        traceSpan(
            component = "Downloader",
            operation = "storeChunk",
            attributes = mapOf(
                "jobId" to jobId,
                "label" to label,
                "chunkIndex" to chunkIndex.toString(),
                "totalChunks" to totalChunks.toString(),
                "bytes" to bytes.toString()
            )
        ) {
            log.withOperation("storeChunk").d { "Storing chunk ${chunkIndex + 1}/$totalChunks for $label ($bytes bytes, jobId=$jobId)" }
            delay(150) // simulate disk write
            log.withOperation("storeChunk").d { "Chunk ${chunkIndex + 1}/$totalChunks stored for $label" }
        }
    }
}
