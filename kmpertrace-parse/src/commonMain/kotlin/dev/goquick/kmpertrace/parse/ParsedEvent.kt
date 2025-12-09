package dev.goquick.kmpertrace.parse

/**
 * Parsed representation of a structured KmperTrace log line.
 */
data class ParsedEvent(
    val traceId: String,
    val spanId: String,
    val parentSpanId: String?,
    val eventKind: EventKind,
    val spanName: String?,
    val durationMs: Long?,
    val loggerName: String?,
    val timestamp: String?,
    val message: String?,
    val sourceComponent: String?,
    val sourceOperation: String?,
    val sourceLocationHint: String?,
    val sourceFile: String?,
    val sourceLine: Int?,
    val sourceFunction: String?,
    val rawFields: Map<String, String>
)
