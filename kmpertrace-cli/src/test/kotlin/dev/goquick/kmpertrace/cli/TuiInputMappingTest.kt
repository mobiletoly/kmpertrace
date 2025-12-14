package dev.goquick.kmpertrace.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class TuiInputMappingTest {
    @Test
    fun key_mapping_matches_expected_behavior() {
        assertEquals(listOf(UiEvent.ToggleHelp), uiEventsForKey('?'))
        assertEquals(listOf(UiEvent.DismissHelp), uiEventsForKey('x'))
        assertEquals(listOf(UiEvent.DismissHelp, UiEvent.Clear), uiEventsForKey('c'))
        assertEquals(listOf(UiEvent.DismissHelp, UiEvent.PromptSearch), uiEventsForKey('/'))
    }

    @Test
    fun command_mapping_matches_expected_behavior() {
        assertEquals(listOf(UiEvent.ToggleHelp), uiEventsForCommand("?"))
        assertEquals(listOf(UiEvent.DismissHelp), uiEventsForCommand("unknown"))
        assertEquals(listOf(UiEvent.DismissHelp, UiEvent.Clear), uiEventsForCommand("/clear"))
        assertEquals(listOf(UiEvent.DismissHelp, UiEvent.CycleStructured), uiEventsForCommand("struct-logs"))
    }
}

