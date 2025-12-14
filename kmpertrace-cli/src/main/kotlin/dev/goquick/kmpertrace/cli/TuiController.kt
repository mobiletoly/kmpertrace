package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.analysis.AnalysisEngine
import dev.goquick.kmpertrace.analysis.AnalysisSnapshot
import dev.goquick.kmpertrace.analysis.FilterState
import dev.goquick.kmpertrace.parse.ParsedLogRecord

internal data class RenderData(
    val snapshot: AnalysisSnapshot,
    val rawList: List<ParsedLogRecord>
)

internal data class ControllerUpdate(
    val forceRender: Boolean = false,
    val rawDirty: Boolean = false,
    val exitRequested: Boolean = false
)

internal class TuiController(
    private val engine: AnalysisEngine,
    private val filters: FilterState,
    private val maxRecords: Int,
    private val promptSearch: () -> String?
) {
    var state: UiState = UiState(
        helpVisible = false,
        search = null,
        minLevel = filters.minLevel,
        rawEnabled = false,
        rawLevel = RawLogLevel.OFF,
        spanAttrsMode = SpanAttrsMode.OFF
    )
        private set

    private val rawLines = ArrayDeque<ParsedLogRecord>()
    private var rawDirty = false

    fun setInitialModes(rawLogsLevel: RawLogLevel, spanAttrsMode: SpanAttrsMode) {
        state = state.copy(
            rawEnabled = rawLogsLevel != RawLogLevel.OFF,
            rawLevel = rawLogsLevel,
            spanAttrsMode = spanAttrsMode
        )
    }

    fun buildRenderData(): RenderData {
        val snapshot = applySearchFilter(engine.snapshot(), state.search)
        val rawList = if (state.rawEnabled) {
            val term = state.search
            val predicate = filters.predicate()
            rawLines.filter { evt ->
                rawLevelAllows(evt, state.rawLevel) &&
                    predicate(evt) &&
                    (term.isNullOrBlank() || matchesRecord(evt, term.trim()))
            }
        } else emptyList()
        return RenderData(snapshot, rawList)
    }

    fun snapshot(): AnalysisSnapshot = engine.snapshot()

    fun rawDirtyAndClear(): Boolean = rawDirty.also { rawDirty = false }

    fun isRawDirty(): Boolean = rawDirty

    fun clearRawDirty() {
        rawDirty = false
    }

    fun handleUiEvent(event: UiEvent, allowInput: Boolean): ControllerUpdate {
        val res = reduceUi(state, event)
        state = res.state
        var force = res.requestRender
        var exit = false

        res.effects.forEach { effect ->
            when (effect) {
                UiEffect.ClearBuffers -> {
                    engine.reset()
                    rawLines.clear()
                    rawDirty = false
                    force = true
                }
                is UiEffect.UpdateMinLevelFilter -> engine.updateFilter(filters.copy(minLevel = effect.minLevel))
                UiEffect.PromptSearch -> {
                    if (allowInput) {
                        val term = promptSearch()
                        val setRes = reduceUi(state, UiEvent.SetSearch(term))
                        state = setRes.state
                        if (setRes.requestRender) force = true
                    }
                }
                UiEffect.Quit -> exit = true
            }
        }

        return ControllerUpdate(forceRender = force, rawDirty = rawDirty, exitRequested = exit)
    }

    fun handleLine(line: String): ControllerUpdate {
        val update = engine.ingest(line)
        update.rawCandidates.forEach { candidate ->
            rawRecordFromLine(candidate, RawLogLevel.ALL)?.let { evt ->
                rawLines += evt
                while (rawLines.size > maxRecords) rawLines.removeFirst()
                if (state.rawEnabled) rawDirty = true
            }
        }
        val force = update.recordsAdded > 0
        return ControllerUpdate(forceRender = force, rawDirty = rawDirty, exitRequested = false)
    }

    fun flush(): ControllerUpdate {
        val update = engine.flush()
        update.rawCandidates.forEach { candidate ->
            rawRecordFromLine(candidate, RawLogLevel.ALL)?.let { evt ->
                rawLines += evt
                while (rawLines.size > maxRecords) rawLines.removeFirst()
                if (state.rawEnabled) rawDirty = true
            }
        }
        val force = update.recordsAdded > 0
        return ControllerUpdate(forceRender = force, rawDirty = rawDirty, exitRequested = false)
    }
}
