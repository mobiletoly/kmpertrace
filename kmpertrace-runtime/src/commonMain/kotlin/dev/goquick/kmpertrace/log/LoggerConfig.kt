package dev.goquick.kmpertrace.log

import dev.goquick.kmpertrace.core.Level

/**
 * Internal runtime configuration for KmperTrace logging/tracing.
 *
 * Public configuration should go through [KmperTrace].
 */
internal object LoggerConfig {

    /**
     * Minimum level allowed through filtering.
     */
    var minLevel: Level = Level.DEBUG

    /**
     * Service name recorded on each log record when provided.
     */
    var serviceName: String? = null

    /**
     * Environment name recorded on each log record when provided.
     */
    var environment: String? = null

    /**
     * Active sinks; empty list disables emission.
     */
    var sinks: List<LogSink> = emptyList()

    /**
     * Whether platform sinks should render glyph icons (e.g., ℹ️/⚠️/❌) before lines.
     */
    var renderGlyphs: Boolean = true

    /**
     * Whether debug span attributes should be emitted into log lines.
     *
     * This is intended for dev-only or sensitive fields you don't want written to logs in release builds.
     * Public APIs mark debug attributes with a leading `?` in the key, and they are emitted on `SPAN_END`
     * as fields whose keys start with `d:`.
     *
     * When disabled, any span attributes whose keys start with `d:` are dropped before emission.
     */
    var emitDebugAttributes: Boolean = false

    /**
     * Additional filter predicate evaluated before emitting to sinks.
     */
    var filter: (LogRecord) -> Boolean = { true }
}
