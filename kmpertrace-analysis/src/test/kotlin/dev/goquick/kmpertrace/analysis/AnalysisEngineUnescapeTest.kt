package dev.goquick.kmpertrace.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalysisEngineUnescapeTest {

    @Test
    fun `unescape percents before parse`() {
        val engine = AnalysisEngine()
        val rawLine =
            """INFO Logger: fatal at 66%% | |{ ts=2025-12-07T00:00:00.000Z lvl=info trace=abc span=1 parent=0 ev=LOG head="fatal at 66%%" log=Logger thread="main" }|"""

        engine.onLine(rawLine)
        val snapshot = engine.snapshot()
        assertTrue(snapshot.traces.isNotEmpty(), "trace should be parsed")
        val event = snapshot.traces.first().spans.first().events.first()
        assertEquals("fatal at 66%", event.message)
    }

    @Test
    fun `chunk markers are stripped even if they slip through`() {
        val engine = AnalysisEngine()
        val raw =
            """INFO Logger: oops (abcd1234:kmpert...) still here | |{ ts=2025-12-07T00:00:00.000Z lvl=info trace=abc span=1 parent=0 ev=LOG head="oops (abcd1234:kmpert...) still here" log=Logger thread="main" }|"""
        engine.onLine(raw)
        val event = engine.snapshot().traces.first().spans.first().events.first()
        assertEquals("""oops  still here""", event.message)
    }

    @Test
    fun `sanitizes structured fields as well`() {
        val engine = AnalysisEngine()
        val raw =
            """ERROR Logger: fail | |{ ts=2025-12-07T00:00:00.000Z lvl=error trace=abc span=1 parent=0 ev=SPAN_END name="Download" dur=10 thread="main" status="ERROR" error_message="Fatal at 50%% (dead)" stack_trace="line1 (abcd1234:kmpert...)\nline2 (abcd1234:kmpert!)" log=Logger }|"""
        engine.onLine(raw)
        val event = engine.snapshot().traces.first().spans.first().events.first()
        assertEquals("Fatal at 50% (dead)", event.rawFields["error_message"])
        val cleanedStack = event.rawFields["stack_trace"] ?: error("stack_trace missing")
        assertTrue("(kmpert" !in cleanedStack, "chunk markers should be removed from stack")
        assertTrue("%%" !in cleanedStack, "percents should be unescaped in stack")
    }
}
