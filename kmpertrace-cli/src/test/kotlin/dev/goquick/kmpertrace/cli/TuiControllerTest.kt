package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.analysis.AnalysisEngine
import dev.goquick.kmpertrace.analysis.FilterState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TuiControllerTest {
    @Test
    fun prompt_search_updates_state_via_effect() {
        val engine = AnalysisEngine(filterState = FilterState(), maxRecords = 100)
        val controller = TuiController(
            engine = engine,
            filters = FilterState(),
            maxRecords = 100,
            promptSearch = { "foo" }
        )
        controller.setInitialModes(rawLogsLevel = RawLogLevel.OFF, spanAttrsMode = SpanAttrsMode.OFF)

        val update = controller.handleUiEvent(UiEvent.PromptSearch, allowInput = true)
        assertTrue(update.forceRender, "prompt should trigger a render")
        assertEquals("foo", controller.state.search)
    }

    @Test
    fun clear_resets_engine_snapshot() {
        val engine = AnalysisEngine(filterState = FilterState(), maxRecords = 100)
        val controller = TuiController(
            engine = engine,
            filters = FilterState(),
            maxRecords = 100,
            promptSearch = { null }
        )
        controller.setInitialModes(rawLogsLevel = RawLogLevel.OFF, spanAttrsMode = SpanAttrsMode.OFF)

        controller.handleLine(
            """t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=root parent=- kind=LOG name="root" dur=0 head="hello" }|"""
        )
        assertTrue(controller.snapshot().traces.isNotEmpty(), "expected trace to be present after ingestion")

        val cleared = controller.handleUiEvent(UiEvent.Clear, allowInput = true)
        assertTrue(cleared.forceRender)
        assertTrue(controller.snapshot().traces.isEmpty(), "expected traces to be cleared")
    }

    @Test
    fun raw_dirty_is_set_only_when_raw_enabled() {
        val engine = AnalysisEngine(filterState = FilterState(), maxRecords = 100)
        val controller = TuiController(
            engine = engine,
            filters = FilterState(),
            maxRecords = 100,
            promptSearch = { null }
        )

        controller.setInitialModes(rawLogsLevel = RawLogLevel.OFF, spanAttrsMode = SpanAttrsMode.OFF)
        val off = controller.handleLine("D/Foo: hello")
        assertFalse(off.rawDirty)

        controller.setInitialModes(rawLogsLevel = RawLogLevel.ALL, spanAttrsMode = SpanAttrsMode.OFF)
        val on = controller.handleLine("D/Foo: hello")
        assertTrue(on.rawDirty)
    }
}
