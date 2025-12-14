package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.analysis.FilterState
import dev.goquick.kmpertrace.analysis.AnalysisEngine
import dev.goquick.kmpertrace.parse.ParsedLogRecord
import dev.goquick.kmpertrace.parse.LogRecordKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TuiRawFilterTest {
    @Test
    fun raw_respects_search_and_filters() {
        val engine = AnalysisEngine(filterState = FilterState())
        val rawLines = ArrayDeque<ParsedLogRecord>()

        // Structured record that matches "foo"
        engine.onLine("t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=root parent=- kind=LOG name=\"-\" dur=0 head=\"foo match\" }|")

        // Raw event that matches search term
        rawLines += ParsedLogRecord(
            traceId = "0",
            spanId = "0",
            parentSpanId = null,
            logRecordKind = LogRecordKind.LOG,
            spanName = "-",
            durationMs = null,
            loggerName = "Raw",
            timestamp = "2025-01-01T00:00:01Z",
            message = "foo raw line",
            sourceComponent = null,
            sourceOperation = null,
            sourceLocationHint = null,
            sourceFile = null,
            sourceLine = null,
            sourceFunction = null,
            rawFields = mutableMapOf("lvl" to "info", "raw" to "true")
        )

        // Raw event that should be filtered out
        rawLines += ParsedLogRecord(
            traceId = "0",
            spanId = "0",
            parentSpanId = null,
            logRecordKind = LogRecordKind.LOG,
            spanName = "-",
            durationMs = null,
            loggerName = "Raw",
            timestamp = "2025-01-01T00:00:02Z",
            message = "other line",
            sourceComponent = null,
            sourceOperation = null,
            sourceLocationHint = null,
            sourceFile = null,
            sourceLine = null,
            sourceFunction = null,
            rawFields = mutableMapOf("lvl" to "info", "raw" to "true")
        )

        val searchTerm = "foo"
        val filteredSnapshot = applySearchFilter(engine.snapshot(), searchTerm)
        val filteredRaw = rawLines.filter { evt ->
            rawLevelAllows(evt, RawLogLevel.ALL) && (searchTerm.isBlank() || matchesRecord(evt, searchTerm))
        }

        // Only the matching raw event remains
        assertEquals(1, filteredRaw.size)
        assertTrue(filteredRaw.first().message?.contains("foo") == true)

        // Structured side still filtered as before
        assertEquals(1, filteredSnapshot.traces.flatMap { it.spans }.flatMap { it.records }.size)
    }
}
