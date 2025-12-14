package dev.goquick.kmpertrace.cli

internal fun uiEventsForKey(key: Char): List<UiEvent> = when (key) {
    'c', 'C' -> listOf(UiEvent.DismissHelp, UiEvent.Clear)
    'a', 'A' -> listOf(UiEvent.DismissHelp, UiEvent.ToggleAttrs)
    'r', 'R' -> listOf(UiEvent.DismissHelp, UiEvent.CycleRaw)
    's', 'S' -> listOf(UiEvent.DismissHelp, UiEvent.CycleStructured)
    '/' -> listOf(UiEvent.DismissHelp, UiEvent.PromptSearch)
    '?' -> listOf(UiEvent.ToggleHelp)
    'q', 'Q' -> listOf(UiEvent.DismissHelp, UiEvent.Quit)
    else -> listOf(UiEvent.DismissHelp)
}

internal fun uiEventsForCommand(line: String): List<UiEvent> {
    val normalized = line.trim().lowercase()
    return when (normalized) {
        "c", "clear", ":clear", "/clear" -> listOf(UiEvent.DismissHelp, UiEvent.Clear)
        "a", "attrs", ":attrs" -> listOf(UiEvent.DismissHelp, UiEvent.ToggleAttrs)
        "r", "raw" -> listOf(UiEvent.DismissHelp, UiEvent.CycleRaw)
        "s", "struct", "struct-logs" -> listOf(UiEvent.DismissHelp, UiEvent.CycleStructured)
        "/", ":search", "search" -> listOf(UiEvent.DismissHelp, UiEvent.PromptSearch)
        "?", "help", ":help" -> listOf(UiEvent.ToggleHelp)
        "q", "quit", ":q" -> listOf(UiEvent.DismissHelp, UiEvent.Quit)
        else -> listOf(UiEvent.DismissHelp)
    }
}

