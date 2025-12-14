package dev.goquick.kmpertrace.parse

/**
 * Structured log record category encoded in logfmt suffix.
 */
enum class LogRecordKind {
    SPAN_START,
    SPAN_END,
    LOG
}
