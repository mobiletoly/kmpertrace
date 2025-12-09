package dev.goquick.kmpertrace.parse

/**
 * Span node in a reconstructed trace tree.
 */
data class SpanNode(
    val spanId: String,
    val parentSpanId: String?,
    val spanName: String,
    val durationMs: Long?,
    val sourceComponent: String?,
    val sourceOperation: String?,
    val sourceLocationHint: String?,
    val sourceFile: String?,
    val sourceLine: Int?,
    val sourceFunction: String?,
    val events: List<ParsedEvent>,
    val children: List<SpanNode>
)

/**
 * Root trace tree containing span hierarchy for a traceId.
 */
data class TraceTree(
    val traceId: String,
    val spans: List<SpanNode>
)
