package dev.goquick.kmpertrace.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TuiStateMachineTest {

    @Test
    fun hides_help_on_any_non_help_event() {
        val start = UiState(helpVisible = true, spanAttrsMode = SpanAttrsMode.OFF)
        val res = reduceUi(start, UiEvent.ToggleAttrs)
        assertFalse(res.state.helpVisible)
        assertEquals(SpanAttrsMode.ON, res.state.spanAttrsMode)
        assertTrue(res.requestRender)
    }

    @Test
    fun dismiss_help_is_noop_when_help_is_not_visible() {
        val start = UiState(helpVisible = false)
        val res = reduceUi(start, UiEvent.DismissHelp)
        assertEquals(start, res.state)
        assertFalse(res.requestRender)
        assertTrue(res.effects.isEmpty())
    }

    @Test
    fun cycle_structured_emits_filter_update_effect() {
        val start = UiState(minLevel = "all")
        val res = reduceUi(start, UiEvent.CycleStructured)
        assertEquals("debug", res.state.minLevel)
        assertTrue(res.effects.contains(UiEffect.UpdateMinLevelFilter("debug")))
        assertTrue(res.requestRender)
    }

    @Test
    fun prompt_search_emits_effect_and_set_search_updates_state() {
        val start = UiState(search = null)
        val prompt = reduceUi(start, UiEvent.PromptSearch)
        assertTrue(prompt.effects.contains(UiEffect.PromptSearch))

        val set = reduceUi(start, UiEvent.SetSearch("foo"))
        assertEquals("foo", set.state.search)
        assertTrue(set.requestRender)
    }

    @Test
    fun clear_emits_clear_buffers_effect() {
        val start = UiState()
        val res = reduceUi(start, UiEvent.Clear)
        assertTrue(res.effects.contains(UiEffect.ClearBuffers))
        assertTrue(res.requestRender)
    }
}

