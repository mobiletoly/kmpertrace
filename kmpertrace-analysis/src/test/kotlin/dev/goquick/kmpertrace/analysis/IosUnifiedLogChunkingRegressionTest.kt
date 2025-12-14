package dev.goquick.kmpertrace.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IosUnifiedLogChunkingRegressionTest {
    @Test
    fun `reassembles chunk markers even when marker is on continuation line`() {
        val engine = AnalysisEngine()

        // Simulate `log stream` output for a *chunked* entry whose message contains embedded newlines.
        // The chunk marker only appears at the end of the OS log entry (the last physical line).
        val id = "6a099778"
        val chunk1Line1 =
            """2025-12-14 17:28:22.590356+0000  SampleApp[123:456] Downloader: boom | |{ ts=2025-12-14T17:28:22.590356Z lvl=error trace=t span=s kind=SPAN_END name="X" dur=1 head="boom" log=Downloader stack_trace="line1"""
        val chunk1Line2 = """line2 ($id:kmpert...)"""

        val chunk2Line1 = """2025-12-14 17:28:22.590357+0000  SampleApp[123:456] continued"""
        val chunk2Line2 = """line3" }| ($id:kmpert!)"""

        // Feed as physical lines.
        listOf(chunk1Line1, chunk1Line2, chunk2Line1, chunk2Line2).forEach { engine.ingest(it) }
        engine.flush()

        val snapshot = engine.snapshot()
        assertTrue(snapshot.traces.isNotEmpty(), "expected parsed trace from reassembled chunks")
        val record = snapshot.traces.first().spans.first().records.first()

        val stack = record.rawFields["stack_trace"]
        assertNotNull(stack, "expected stack_trace to be parsed")
        assertTrue("(kmpert" !in stack, "chunk markers should be removed from stack_trace")
        assertEquals(true, stack.contains("line1"), "expected reconstructed stack to contain line1")
        assertEquals(true, stack.contains("line2"), "expected reconstructed stack to contain line2")
        assertEquals(true, stack.contains("line3"), "expected reconstructed stack to contain line3")
    }
}
