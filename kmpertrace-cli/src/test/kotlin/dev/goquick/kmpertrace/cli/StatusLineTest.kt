package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.analysis.AnalysisEngine
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusLineTest {

    @Test
    fun `error count captures span end with status error`() {
        val line = """2025-01-01T00:00:00Z ERROR Downloader X |{| ts=2025-01-01T00:00:00Z lvl=error log=Downloader trace=trace123 span=span1 parent_span=- kind=SPAN_END name="Downloader.DownloadA" dur=100 thread="main" status="ERROR" err_type="IllegalStateException" err_msg="boom" stack_trace="java.lang.IllegalStateException: boom" }|"""
        val engine = AnalysisEngine()
        engine.onLine(line)
        val snapshot = engine.snapshot()
        assertEquals(1, errorCount(snapshot))
    }

    @Test
    fun `error count includes nested child span errors`() {
        val parentStart = """2025-01-01T00:00:00Z INFO Vm X |{| ts=2025-01-01T00:00:00Z lvl=info log=Vm trace=trace123 span=root parent_span=- kind=SPAN_START name="Root" dur=0 }|"""
        val childEnd = """2025-01-01T00:00:01Z ERROR Downloader X |{| ts=2025-01-01T00:00:01Z lvl=error log=Downloader trace=trace123 span=child parent_span=root kind=SPAN_END name="Downloader.Child" dur=100 thread="main" status="ERROR" err_type="IllegalStateException" err_msg="boom" stack_trace="java.lang.IllegalStateException: boom" }|"""
        val engine = AnalysisEngine()
        engine.onLine(parentStart)
        engine.onLine(childEnd)
        val snapshot = engine.snapshot()
        assertEquals(1, errorCount(snapshot))
    }

    @Test
    fun `error count stays zero for non-error log`() {
        val line = """2025-01-01T00:00:00Z INFO Downloader X |{| ts=2025-01-01T00:00:00Z lvl=info log=Downloader trace=trace123 span=span1 parent_span=- kind=LOG name="Downloader.DownloadA" dur=0 thread="main" head="hello" }|"""
        val engine = AnalysisEngine()
        engine.onLine(line)
        val snapshot = engine.snapshot()
        assertEquals(0, errorCount(snapshot))
    }
}
