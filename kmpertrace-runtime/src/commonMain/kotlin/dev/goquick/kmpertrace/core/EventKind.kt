package dev.goquick.kmpertrace.core

/**
 * Structured event kind used in log output.
 */
enum class EventKind {
    SPAN_START,
    SPAN_END,
    LOG
}
