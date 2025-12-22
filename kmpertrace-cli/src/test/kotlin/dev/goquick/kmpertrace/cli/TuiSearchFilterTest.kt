package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.analysis.AnalysisEngine
import dev.goquick.kmpertrace.analysis.FilterState
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertTrue

class TuiSearchFilterTest {

    @Test
    fun search_by_trace_id_keeps_trace() {
        val engine = AnalysisEngine(filterState = FilterState())
        engine.onLine(
            "t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_START name=\"root\" dur=0 head=\"start\" }|"
        )

        val filtered = applySearchFilter(engine.snapshot(), "trace-1")
        val rendered = renderTraces(filtered.traces, filtered.untraced, colorize = false, timeFormat = TimeFormat.TIME_ONLY, zoneId = ZoneOffset.UTC)

        assertTrue("trace trace-1" in rendered, "expected trace to remain when searching by trace id:\n$rendered")
    }

    @Test
    fun search_by_span_id_keeps_span_records_even_when_records_dont_match_term() {
        val engine = AnalysisEngine(filterState = FilterState())
        engine.onLine(
            "t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=span-123 parent=- kind=SPAN_START name=\"root\" dur=0 head=\"start\" }|"
        )
        engine.onLine(
            "t INFO Api |{ ts=2025-01-01T00:00:01Z lvl=info log=Api trace=trace-1 span=span-123 parent=- kind=LOG name=\"root\" dur=0 head=\"hello\" }|"
        )

        val filtered = applySearchFilter(engine.snapshot(), "span-123")
        val rendered = renderTraces(filtered.traces, filtered.untraced, colorize = false, timeFormat = TimeFormat.TIME_ONLY, zoneId = ZoneOffset.UTC)

        assertTrue("trace trace-1" in rendered, "expected trace to remain when searching by span id:\n$rendered")
        assertTrue("Api: hello" in rendered, "expected span's log line to remain when searching by span id:\n$rendered")
    }
}

