package dev.goquick.kmpertrace.cli

internal data class UiState(
    val helpVisible: Boolean = false,
    val search: String? = null,
    val minLevel: String? = null,
    val rawEnabled: Boolean = false,
    val rawLevel: RawLogLevel = RawLogLevel.OFF,
    val spanAttrsMode: SpanAttrsMode = SpanAttrsMode.OFF
)

internal sealed interface UiEvent {
    data object Clear : UiEvent
    data object DismissHelp : UiEvent
    data object ToggleHelp : UiEvent
    data object ToggleAttrs : UiEvent
    data object CycleRaw : UiEvent
    data object CycleStructured : UiEvent
    data object PromptSearch : UiEvent
    data class SetSearch(val term: String?) : UiEvent
    data object Quit : UiEvent
}

internal sealed interface UiEffect {
    data object ClearBuffers : UiEffect
    data class UpdateMinLevelFilter(val minLevel: String?) : UiEffect
    data object PromptSearch : UiEffect
    data object Quit : UiEffect
}

internal data class ReduceResult(
    val state: UiState,
    val effects: List<UiEffect> = emptyList(),
    val requestRender: Boolean = false
)

internal fun reduceUi(state: UiState, event: UiEvent): ReduceResult {
    var nextState = state
    val effects = mutableListOf<UiEffect>()
    var requestRender = false

    // Safety: any interaction closes help (even if a caller forgot to emit DismissHelp explicitly).
    if (state.helpVisible && event != UiEvent.ToggleHelp && event != UiEvent.DismissHelp) {
        nextState = nextState.copy(helpVisible = false)
        requestRender = true
    }

    when (event) {
        UiEvent.Clear -> {
            effects += UiEffect.ClearBuffers
            requestRender = true
        }

        UiEvent.DismissHelp -> {
            if (nextState.helpVisible) {
                nextState = nextState.copy(helpVisible = false)
                requestRender = true
            }
        }

        UiEvent.ToggleHelp -> {
            nextState = nextState.copy(helpVisible = !nextState.helpVisible)
            requestRender = true
        }

        UiEvent.ToggleAttrs -> {
            nextState = nextState.copy(spanAttrsMode = nextSpanAttrsMode(nextState.spanAttrsMode))
            requestRender = true
        }

        UiEvent.CycleRaw -> {
            val next = nextRawLevel(nextState.rawEnabled, nextState.rawLevel)
            nextState = nextState.copy(rawEnabled = next != RawLogLevel.OFF, rawLevel = next)
            requestRender = true
        }

        UiEvent.CycleStructured -> {
            val next = nextStructuredLevel(nextState.minLevel)
            nextState = nextState.copy(minLevel = next)
            effects += UiEffect.UpdateMinLevelFilter(next)
            requestRender = true
        }

        UiEvent.PromptSearch -> {
            effects += UiEffect.PromptSearch
        }

        is UiEvent.SetSearch -> {
            nextState = nextState.copy(search = event.term)
            requestRender = true
        }

        UiEvent.Quit -> {
            effects += UiEffect.Quit
        }
    }

    return ReduceResult(nextState, effects, requestRender)
}

internal fun nextStructuredLevel(current: String?): String? = when (current) {
    null, "all" -> "debug"
    "debug" -> "info"
    "info" -> "error"
    "error" -> "all"
    else -> "debug"
}

