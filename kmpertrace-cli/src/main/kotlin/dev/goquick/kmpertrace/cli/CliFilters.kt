package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.analysis.AnalysisSnapshot
import dev.goquick.kmpertrace.parse.LogRecordKind
import dev.goquick.kmpertrace.parse.ParsedLogRecord
import dev.goquick.kmpertrace.parse.SpanNode
import dev.goquick.kmpertrace.parse.TraceTree

internal fun applySearchFilter(snapshot: AnalysisSnapshot, term: String?): AnalysisSnapshot {
    val trimmed = term?.trim()
    if (trimmed.isNullOrEmpty()) return snapshot
    val filteredTraces = snapshot.traces.mapNotNull { trace ->
        if (containsTerm(trace.traceId, trimmed)) return@mapNotNull trace
        val spans = trace.spans.mapNotNull { span -> filterSpanByTerm(span, trimmed) }
        if (spans.isEmpty()) null else trace.copy(spans = spans)
    }
    val filteredUntraced = snapshot.untraced.filter { record -> matchesRecord(record, trimmed) }
    return snapshot.copy(traces = filteredTraces, untraced = filteredUntraced)
}

internal fun filterErrorsOnly(snapshot: AnalysisSnapshot): AnalysisSnapshot {
    val filteredTraces = snapshot.traces.mapNotNull { filterTraceErrors(it) }
    val filteredUntraced = snapshot.untraced.filter { isErrorRecord(it) }
    return snapshot.copy(traces = filteredTraces, untraced = filteredUntraced)
}

internal fun errorCount(snapshot: AnalysisSnapshot): Int =
    snapshot.traces.sumOf { trace ->
        trace.spans.sumOf { spanErrorCount(it) }
    } + snapshot.untraced.count { record -> isErrorRecord(record) }

private fun filterTraceErrors(trace: TraceTree): TraceTree? {
    val spans = trace.spans.mapNotNull { filterSpanErrors(it) }
    return if (spans.isEmpty()) null else TraceTree(trace.traceId, spans)
}

private fun filterSpanErrors(span: SpanNode): SpanNode? {
    val filteredChildren = span.children.mapNotNull { filterSpanErrors(it) }
    if (!spanHasError(span) && filteredChildren.isEmpty()) return null
    return span.copy(children = filteredChildren)
}

private fun spanErrorCount(span: SpanNode): Int {
    val self = if (spanHasError(span)) 1 else 0
    val children = span.children.sumOf { child -> spanErrorCount(child) }
    return self + children
}

private fun spanHasError(span: SpanNode): Boolean =
    span.records.any { record -> isErrorRecord(record) }

private fun isErrorRecord(record: ParsedLogRecord): Boolean =
    record.rawFields["status"]?.equals("error", ignoreCase = true) == true ||
            record.rawFields["lvl"]?.equals("error", ignoreCase = true) == true ||
            record.rawFields["throwable"] != null ||
            record.rawFields["err_type"] != null ||
            (record.logRecordKind == LogRecordKind.SPAN_END && record.rawFields["err_msg"] != null)

private fun filterSpanByTerm(span: SpanNode, term: String): SpanNode? {
    if (matchesSpan(span, term)) return span
    val childMatches = span.children.mapNotNull { child -> filterSpanByTerm(child, term) }
    val recordMatches = span.records.filter { record -> matchesRecord(record, term) }
    return if (childMatches.isNotEmpty() || recordMatches.isNotEmpty()) {
        span.copy(records = recordMatches, children = childMatches)
    } else null
}

private fun matchesSpan(span: SpanNode, term: String): Boolean =
    containsTerm(span.spanId, term) ||
            containsTerm(span.parentSpanId, term) ||
            containsTerm(span.spanName, term) ||
            containsTerm(span.sourceComponent, term) ||
            containsTerm(span.sourceOperation, term) ||
            containsTerm(span.sourceLocationHint, term) ||
            containsTerm(span.sourceFile, term) ||
            containsTerm(span.sourceFunction, term)

internal fun matchesRecord(record: ParsedLogRecord, term: String): Boolean =
    containsTerm(record.traceId, term) ||
            containsTerm(record.spanId, term) ||
            containsTerm(record.parentSpanId, term) ||
            containsTerm(record.message, term) ||
            containsTerm(record.loggerName, term) ||
            containsTerm(record.sourceComponent, term) ||
            containsTerm(record.sourceOperation, term) ||
            containsTerm(record.rawFields["stack_trace"], term) ||
            containsTerm(record.spanName, term) ||
            containsTerm(record.rawFields["name"], term) ||
            combinedMatch(record, term)

private fun containsTerm(value: String?, term: String): Boolean =
    value?.contains(term, ignoreCase = true) == true

private fun combinedMatch(record: ParsedLogRecord, term: String): Boolean {
    val logger = record.loggerName
    val msg = record.message
    if (!logger.isNullOrBlank() && !msg.isNullOrBlank()) {
        val combo = "$logger: $msg"
        if (combo.contains(term, ignoreCase = true)) return true
    }
    return false
}
