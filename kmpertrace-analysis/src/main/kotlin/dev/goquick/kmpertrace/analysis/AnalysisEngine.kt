package dev.goquick.kmpertrace.analysis

import dev.goquick.kmpertrace.parse.ParsedLogRecord
import dev.goquick.kmpertrace.parse.buildTraces
import dev.goquick.kmpertrace.parse.parseLine

/**
 * Streaming ingestion + trace aggregation for KmperTrace logs.
 *
 * This is UI-agnostic so TUI/IDE plugins can share the same core.
 */
class AnalysisEngine(
    private var filterState: FilterState = FilterState(),
    private val maxRecords: Int = 5_000
) {
    private val androidGrouper = AndroidMultilineGrouper()
    private val iosGrouper = IosUnifiedLogMultilineGrouper()
    private val chunkAssembler = ChunkAssembler()
    private val structuredFramer = StructuredSuffixFramer()
    private val records = ArrayDeque<ParsedLogRecord>()
    private var dropped = 0

    /**
     * Feed a single raw log line. Parsed log records are retained for snapshots.
     */
    fun onLine(line: String) {
        ingest(line)
    }

    /**
     * Feed a raw log line and return any "raw log" candidates that are not part of a structured frame.
     *
     * This keeps framing/ingestion logic centralized so the CLI (and future IDE plugins) can share it,
     * while still allowing UIs to implement their own raw log parsing/filtering.
     */
    fun ingest(line: String): IngestUpdate {
        val rawCandidates = mutableListOf<String>()
        var recordsAdded = 0

        androidGrouper.feed(line).forEach { collapsed ->
            recordsAdded += ingestCollapsedLine(collapsed, rawCandidates)
        }

        return IngestUpdate(
            rawCandidates = rawCandidates,
            recordsAdded = recordsAdded
        )
    }

    /**
     * Flush any buffered partial ingestion state (Android grouping and structured framing).
     */
    fun flush(): IngestUpdate {
        val rawCandidates = mutableListOf<String>()
        var recordsAdded = 0

        androidGrouper.flush().forEach { collapsed ->
            recordsAdded += ingestCollapsedLine(collapsed, rawCandidates)
        }

        iosGrouper.flush().forEach { entry ->
            recordsAdded += ingestUnifiedEntry(entry, rawCandidates)
        }

        structuredFramer.flush().forEach { candidate ->
            val parsed = parseLine(candidate)?.sanitizeText() ?: return@forEach
            addRecord(parsed)
            recordsAdded++
        }

        return IngestUpdate(
            rawCandidates = rawCandidates,
            recordsAdded = recordsAdded
        )
    }

    private fun ingestCollapsedLine(line: String, rawCandidates: MutableList<String>): Int {
        var added = 0
        iosGrouper.feed(line).forEach { entry ->
            added += ingestUnifiedEntry(entry, rawCandidates)
        }
        return added
    }

    private fun ingestUnifiedEntry(entry: String, rawCandidates: MutableList<String>): Int {
        var added = 0

        val completeLines = chunkAssembler.feed(entry)
        completeLines.forEach { full ->
            val cleaned = stripChunkMarkers(unescapePercents(full))

            val openStructured = structuredFramer.isOpenStructured()
            if (!openStructured && !cleaned.contains("|{")) {
                rawCandidates += cleaned
            }

            structuredFramer.feed(cleaned).forEach { candidate ->
                val parsed = parseLine(candidate)?.sanitizeText() ?: return@forEach
                addRecord(parsed)
                added++
            }
        }

        return added
    }

    private fun addRecord(parsed: ParsedLogRecord) {
        records.addLast(parsed)
        if (records.size > maxRecords) {
            records.removeFirst()
            dropped++
        }
    }

    /**
     * Current snapshot of trace trees + untraced log records.
     */
    fun snapshot(): AnalysisSnapshot {
        val current = records.filter(filterState.predicate())
        return AnalysisSnapshot(
            traces = buildTraces(current),
            untraced = current.filter { it.traceId == "0" },
            droppedCount = dropped
        )
    }

    /**
     * Clear all buffered log records.
     */
    fun reset() {
        records.clear()
        dropped = 0
        androidGrouper.reset()
        iosGrouper.reset()
        structuredFramer.clear()
        chunkAssembler.reset()
    }

    fun updateFilter(newFilter: FilterState) {
        filterState = newFilter
    }
}

data class IngestUpdate(
    val rawCandidates: List<String>,
    val recordsAdded: Int
)

data class AnalysisSnapshot(
    val traces: List<dev.goquick.kmpertrace.parse.TraceTree>,
    val untraced: List<ParsedLogRecord>,
    val droppedCount: Int
)

internal fun unescapePercents(text: String): String =
    // iOS backend escapes % as %% for NSLog; undo that so rendered output looks correct.
    text.replace("%%", "%")

private val chunkMarkerAnywhere =
    Regex("""\([\da-fA-F]{4,32}:kmpert(?:\.\.\.|!)\)""")

internal fun stripChunkMarkers(text: String): String =
    text.replace(chunkMarkerAnywhere, "").trimEnd()

private fun ParsedLogRecord.sanitizeText(): ParsedLogRecord {
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
