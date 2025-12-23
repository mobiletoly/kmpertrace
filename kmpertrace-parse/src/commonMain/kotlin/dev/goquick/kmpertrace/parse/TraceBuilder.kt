package dev.goquick.kmpertrace.parse

/**
 * Build trace trees from parsed log records.
 *
 * Untraced records (wire field `trace=0`, i.e. [ParsedLogRecord.traceId] == `"0"`) are excluded from
 * trace trees but can still be kept in [ParsedLogRecord] lists if desired upstream.
 */
fun buildTraces(records: List<ParsedLogRecord>): List<TraceTree> {
    val traces = mutableListOf<TraceTree>()

    records.groupBy { it.traceId }
        .filterKeys { it != "0" }
        .forEach { (traceId, traceRecords) ->
            val spanBuilders = linkedMapOf<String, SpanBuilder>()

            traceRecords.forEachIndexed { idx, record ->
                val builder = spanBuilders.getOrPut(record.spanId) { SpanBuilder() }
                if (builder.firstSeenIndex == null) builder.firstSeenIndex = idx
                if (record.timestamp != null) {
                    if (builder.startTimestamp == null || record.logRecordKind == LogRecordKind.SPAN_START) {
                        builder.startTimestamp = record.timestamp
                    }
                }
                if (builder.spanKind == null && record.logRecordKind == LogRecordKind.SPAN_START) {
                    builder.spanKind = record.rawFields["span_kind"]
                }
                if (builder.parentSpanId == null && record.parentSpanId != null) {
                    builder.parentSpanId = record.parentSpanId
                }
                if (builder.spanName == null && record.spanName != null) {
                    builder.spanName = record.spanName
                }
                if (record.logRecordKind == LogRecordKind.SPAN_END && record.durationMs != null) {
                    builder.durationMs = record.durationMs
                } else if (builder.durationMs == null && record.durationMs != null) {
                    builder.durationMs = record.durationMs
                }
                if (record.logRecordKind == LogRecordKind.SPAN_START || record.logRecordKind == LogRecordKind.SPAN_END) {
                    val extracted = extractSpanAttributes(record.rawFields)
                    if (extracted.isNotEmpty()) {
                        builder.attributes = (builder.attributes + extracted).toSortedMap()
                    }
                }
                if (builder.sourceComponent == null && record.sourceComponent != null) builder.sourceComponent = record.sourceComponent
                if (builder.sourceOperation == null && record.sourceOperation != null) builder.sourceOperation = record.sourceOperation
                if (builder.sourceLocationHint == null && record.sourceLocationHint != null) builder.sourceLocationHint = record.sourceLocationHint
                if (builder.sourceFile == null && record.sourceFile != null) builder.sourceFile = record.sourceFile
                if (builder.sourceLine == null && record.sourceLine != null) builder.sourceLine = record.sourceLine
                if (builder.sourceFunction == null && record.sourceFunction != null) builder.sourceFunction = record.sourceFunction
                if (record.logRecordKind == LogRecordKind.LOG) {
                    builder.logs.add(record)
                } else if (record.logRecordKind == LogRecordKind.SPAN_END && record.rawFields.containsKey("stack_trace")) {
                    // Surface span-end errors (with stack traces) alongside logs for rendering.
                    builder.logs.add(record)
                }
            }

            val nodes = spanBuilders.mapValues { (spanId, b) ->
                MutableSpanNode(
                    spanId = spanId,
                    parentSpanId = b.parentSpanId,
                    spanName = b.spanName ?: spanId,
                    spanKind = b.spanKind,
                    durationMs = b.durationMs,
                    startTimestamp = b.startTimestamp,
                    sourceComponent = b.sourceComponent,
                    sourceOperation = b.sourceOperation,
                    sourceLocationHint = b.sourceLocationHint,
                    sourceFile = b.sourceFile,
                    sourceLine = b.sourceLine,
                    sourceFunction = b.sourceFunction,
                    attributes = b.attributes,
                    records = b.logs.toList(),
                    firstSeenIndex = b.firstSeenIndex ?: Int.MAX_VALUE
                )
            }

            val roots = mutableListOf<MutableSpanNode>()
            nodes.values.forEach { node ->
                val parentId = node.parentSpanId
                if (parentId != null) {
                    val parent = nodes[parentId]
                    if (parent != null) {
                        parent.children.add(node)
                    } else {
                        roots.add(node)
                    }
                } else {
                    roots.add(node)
                }
            }

            roots.sortWith(spanOrderComparator)
            roots.forEach { sortChildren(it) }
            traces += TraceTree(
                traceId = traceId,
                spans = roots.map { it.toSpanNode() }
            )
        }

    return traces
}

private data class SpanBuilder(
    var parentSpanId: String? = null,
    var spanName: String? = null,
    var spanKind: String? = null,
    var durationMs: Long? = null,
    var sourceComponent: String? = null,
    var sourceOperation: String? = null,
    var sourceLocationHint: String? = null,
    var sourceFile: String? = null,
    var sourceLine: Int? = null,
    var sourceFunction: String? = null,
    var firstSeenIndex: Int? = null,
    var startTimestamp: String? = null,
    var attributes: Map<String, String> = emptyMap(),
    val logs: MutableList<ParsedLogRecord> = mutableListOf()
)

private class MutableSpanNode(
    val spanId: String,
    val parentSpanId: String?,
    val spanName: String,
    val spanKind: String?,
    val durationMs: Long?,
    val startTimestamp: String?,
    val sourceComponent: String?,
    val sourceOperation: String?,
    val sourceLocationHint: String?,
    val sourceFile: String?,
    val sourceLine: Int?,
    val sourceFunction: String?,
    val attributes: Map<String, String>,
    val records: List<ParsedLogRecord>,
    val firstSeenIndex: Int,
    val children: MutableList<MutableSpanNode> = mutableListOf()
) {
    fun toSpanNode(): SpanNode = SpanNode(
        spanId = spanId,
        parentSpanId = parentSpanId,
        spanName = spanName,
        spanKind = spanKind,
        durationMs = durationMs,
        startTimestamp = startTimestamp,
        sourceComponent = sourceComponent,
        sourceOperation = sourceOperation,
        sourceLocationHint = sourceLocationHint,
        sourceFile = sourceFile,
        sourceLine = sourceLine,
        sourceFunction = sourceFunction,
        attributes = attributes,
        records = records,
        children = children.map { it.toSpanNode() }
    )
}

private fun extractSpanAttributes(rawFields: Map<String, String>): Map<String, String> =
    rawFields
        .filterKeys { key -> key.startsWith(ATTRIBUTE_PREFIX) || key.startsWith(DEBUG_ATTRIBUTE_PREFIX) }
        .toSortedMap()

private const val ATTRIBUTE_PREFIX = "a:"
private const val DEBUG_ATTRIBUTE_PREFIX = "d:"

private val spanOrderComparator = compareBy<MutableSpanNode>(
    { it.startTimestamp == null },
    { it.startTimestamp },
    { it.firstSeenIndex },
    { it.spanId }
)

private fun sortChildren(node: MutableSpanNode) {
    node.children.sortWith(spanOrderComparator)
    node.children.forEach { sortChildren(it) }
}
