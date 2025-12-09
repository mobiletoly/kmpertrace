package dev.goquick.kmpertrace.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalysisEngineMessagePreferenceTest {

    @Test
    fun `prefers parsed message over truncated head`() {
        val engine = AnalysisEngine()
        val rawLine =
            """12-08 00:54:42.528  5330  5330 D Downloader: Download DownloadA progress 33% (jobId=-123) |{ ts=2025-12-08T23:05:29.500817Z lvl=debug trace=trace-1 span=span-1 parent=parent-1 ev=LOG head="Download Downlo" src=Downloader/DownloadA log=Downloader thread="main" }|"""

        engine.onLine(rawLine)
        val snapshot = engine.snapshot()
        assertTrue(snapshot.traces.isNotEmpty(), "trace should be parsed")
        val event = snapshot.traces.first().spans.first().events.first()
        assertEquals("Download DownloadA progress 33% (jobId=-123)", event.message)
    }
}
