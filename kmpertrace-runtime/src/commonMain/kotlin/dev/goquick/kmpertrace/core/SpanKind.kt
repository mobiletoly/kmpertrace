package dev.goquick.kmpertrace.core

/**
 * Span semantic type (aligned with OpenTelemetry kinds).
 */
enum class SpanKind {
    INTERNAL,
    /**
     * A user/system journey span (high-level entrypoint triggered by a tap/system event).
     *
     * This is not an OpenTelemetry semantic kind; it is a KmperTrace UI/UX convention used by the CLI.
     */
    JOURNEY,
    SERVER,
    CLIENT,
    PRODUCER,
    CONSUMER
}
