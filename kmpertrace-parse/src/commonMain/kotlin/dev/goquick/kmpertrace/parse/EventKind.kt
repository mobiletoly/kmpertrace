package dev.goquick.kmpertrace.parse

/**
 * Structured event category encoded in logfmt suffix.
 */
enum class EventKind {
    SPAN_START,
    SPAN_END,
    LOG
}
