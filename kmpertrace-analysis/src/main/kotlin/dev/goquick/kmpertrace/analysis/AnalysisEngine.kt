package dev.goquick.kmpertrace.analysis

import dev.goquick.kmpertrace.parse.ParsedEvent
import dev.goquick.kmpertrace.parse.buildTraces
import dev.goquick.kmpertrace.parse.parseLine

/**
 * Streaming ingestion + trace aggregation for KmperTrace logs.
 *
 * This is UI-agnostic so TUI/IDE plugins can share the same core.
 */
class AnalysisEngine(
    private var filterState: FilterState = FilterState(),
    private val maxEvents: Int = 5_000
) {
    private val chunkAssembler = ChunkAssembler()
    private val events = ArrayDeque<ParsedEvent>()
    private var dropped = 0

    /**
     * Feed a single raw log line. Parsed events are retained for snapshots.
     */
    fun onLine(line: String) {
        val completeLines = chunkAssembler.feed(line)
        completeLines.forEach { full ->
            val cleaned = stripChunkMarkers(unescapePercents(full))
            val parsed = parseLine(cleaned)?.sanitizeText() ?: return@forEach
            events.addLast(parsed)
            if (events.size > maxEvents) {
                events.removeFirst()
                dropped++
            }
        }
    }

    /**
     * Current snapshot of trace trees + untraced events.
     */
    fun snapshot(): AnalysisSnapshot {
        val current = events.filter(filterState.predicate())
        return AnalysisSnapshot(
            traces = buildTraces(current),
            untraced = current.filter { it.traceId == "0" },
            droppedCount = dropped
        )
    }

    /**
     * Clear all buffered events.
     */
    fun reset() {
        events.clear()
        dropped = 0
    }

    fun updateFilter(newFilter: FilterState) {
        filterState = newFilter
    }
}

data class AnalysisSnapshot(
    val traces: List<dev.goquick.kmpertrace.parse.TraceTree>,
    val untraced: List<ParsedEvent>,
    val droppedCount: Int
)

internal fun unescapePercents(text: String): String =
    // iOS backend escapes % as %% for NSLog; undo that so rendered output looks correct.
    text.replace("%%", "%")

private val chunkMarkerAnywhere =
    Regex("""\([\da-fA-F]{4,32}:kmpert(?:\.\.\.|!)\)""")

internal fun stripChunkMarkers(text: String): String =
    text.replace(chunkMarkerAnywhere, "").trimEnd()

private fun ParsedEvent.sanitizeText(): ParsedEvent {
    fun clean(value: String?): String? = value
        ?.let { stripChunkMarkers(unescapePercents(it)) }
        ?.trimEnd()
        ?.removeSuffix("|")
        ?.trimEnd()

    val cleanedRaw = rawFields.mapValues { (_, v) -> clean(v) ?: "" }
    val cleanedMessage = clean(message) ?: clean(rawFields["head"])

    return copy(
        message = cleanedMessage,
        spanName = clean(spanName),
        loggerName = clean(loggerName),
        sourceComponent = clean(sourceComponent),
        sourceOperation = clean(sourceOperation),
        sourceLocationHint = clean(sourceLocationHint),
        sourceFile = clean(sourceFile),
        sourceFunction = clean(sourceFunction),
        rawFields = cleanedRaw
    )
}
