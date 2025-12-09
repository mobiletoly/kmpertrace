package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.analysis.AnalysisSnapshot
import dev.goquick.kmpertrace.parse.EventKind
import dev.goquick.kmpertrace.parse.ParsedEvent
import dev.goquick.kmpertrace.parse.SpanNode
import dev.goquick.kmpertrace.parse.TraceTree

internal fun applySearchFilter(snapshot: AnalysisSnapshot, term: String?): AnalysisSnapshot {
    val trimmed = term?.trim()
    if (trimmed.isNullOrEmpty()) return snapshot
    val filteredTraces = snapshot.traces.mapNotNull { trace ->
        val spans = trace.spans.mapNotNull { span -> filterSpanByTerm(span, trimmed) }
        if (spans.isEmpty()) null else trace.copy(spans = spans)
    }
    val filteredUntraced = snapshot.untraced.filter { evt -> matchesEvent(evt, trimmed) }
    return snapshot.copy(traces = filteredTraces, untraced = filteredUntraced)
}

internal fun filterErrorsOnly(snapshot: AnalysisSnapshot): AnalysisSnapshot {
    val filteredTraces = snapshot.traces.mapNotNull { filterTraceErrors(it) }
    val filteredUntraced = snapshot.untraced.filter { isErrorEvent(it) }
    return snapshot.copy(traces = filteredTraces, untraced = filteredUntraced)
}

internal fun errorCount(snapshot: AnalysisSnapshot): Int =
    snapshot.traces.sumOf { trace ->
        trace.spans.sumOf { spanErrorCount(it) }
    } + snapshot.untraced.count { evt -> isErrorEvent(evt) }

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
    span.events.any { evt -> isErrorEvent(evt) }

private fun isErrorEvent(evt: ParsedEvent): Boolean =
    evt.rawFields["status"]?.equals("error", ignoreCase = true) == true ||
            evt.rawFields["lvl"]?.equals("error", ignoreCase = true) == true ||
            evt.rawFields["throwable"] != null ||
            evt.rawFields["error_type"] != null ||
            (evt.eventKind == EventKind.SPAN_END && evt.rawFields["error_message"] != null)

private fun filterSpanByTerm(span: SpanNode, term: String): SpanNode? {
    val childMatches = span.children.mapNotNull { child -> filterSpanByTerm(child, term) }
    val eventMatches = span.events.filter { evt -> matchesEvent(evt, term) }
    val selfMatch = matchesSpan(span, term)
    return if (selfMatch || childMatches.isNotEmpty() || eventMatches.isNotEmpty()) {
        span.copy(events = eventMatches, children = childMatches)
    } else null
}

private fun matchesSpan(span: SpanNode, term: String): Boolean =
    containsTerm(span.spanName, term) ||
            containsTerm(span.sourceComponent, term) ||
            containsTerm(span.sourceOperation, term) ||
            containsTerm(span.sourceLocationHint, term) ||
            containsTerm(span.sourceFile, term) ||
            containsTerm(span.sourceFunction, term)

internal fun matchesEvent(evt: ParsedEvent, term: String): Boolean =
    containsTerm(evt.message, term) ||
            containsTerm(evt.loggerName, term) ||
            containsTerm(evt.sourceComponent, term) ||
            containsTerm(evt.sourceOperation, term) ||
            containsTerm(evt.rawFields["stack_trace"], term) ||
            containsTerm(evt.spanName, term) ||
            containsTerm(evt.rawFields["name"], term) ||
            combinedMatch(evt, term)

private fun containsTerm(value: String?, term: String): Boolean =
    value?.contains(term, ignoreCase = true) == true

private fun combinedMatch(evt: ParsedEvent, term: String): Boolean {
    val logger = evt.loggerName
    val msg = evt.message
    if (!logger.isNullOrBlank() && !msg.isNullOrBlank()) {
        val combo = "$logger: $msg"
        if (combo.contains(term, ignoreCase = true)) return true
    }
    return false
}
