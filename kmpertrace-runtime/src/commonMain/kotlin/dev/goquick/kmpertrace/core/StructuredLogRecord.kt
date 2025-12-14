package dev.goquick.kmpertrace.core

import kotlin.time.Instant

/**
 * Structured representation of a KmperTrace log record.
 */
internal data class StructuredLogRecord(
    val timestamp: Instant,
    val level: Level,
    val loggerName: String,
    val message: String,

    val traceId: String? = null,
    val spanId: String? = null,
    val parentSpanId: String? = null,
    val logRecordKind: LogRecordKind = LogRecordKind.LOG,
    val spanName: String? = null,
    val durationMs: Long? = null,

    val threadName: String? = null,

    val serviceName: String? = null,
    val environment: String? = null,

    // source metadata
    val sourceComponent: String? = null,
    val sourceOperation: String? = null,
    val sourceLocationHint: String? = null,
    val sourceFile: String? = null,
    val sourceLine: Int? = null,
    val sourceFunction: String? = null,

    val attributes: Map<String, String> = emptyMap(),
    val throwable: Throwable? = null
)
