package dev.goquick.kmpertrace.core

/**
 * Structured log record kind used in log output.
 */
enum class LogRecordKind {
    SPAN_START,
    SPAN_END,
    LOG
}
