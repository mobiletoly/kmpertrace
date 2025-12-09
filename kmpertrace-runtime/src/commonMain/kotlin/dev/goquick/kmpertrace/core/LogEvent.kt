package dev.goquick.kmpertrace.core

import kotlin.time.Instant

/**
 * Structured representation of a log or span event emitted by KmperTrace.
 */
data class LogEvent(
    val timestamp: Instant,
    val level: Level,
    val loggerName: String,
    val message: String,

    val traceId: String? = null,
    val spanId: String? = null,
    val parentSpanId: String? = null,
    val eventKind: EventKind = EventKind.LOG,
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
