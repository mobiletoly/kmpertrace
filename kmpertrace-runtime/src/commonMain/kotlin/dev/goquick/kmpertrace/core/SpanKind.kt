package dev.goquick.kmpertrace.core

/**
 * Span semantic type (aligned with OpenTelemetry kinds).
 */
enum class SpanKind {
    INTERNAL,
    SERVER,
    CLIENT,
    PRODUCER,
    CONSUMER
}
