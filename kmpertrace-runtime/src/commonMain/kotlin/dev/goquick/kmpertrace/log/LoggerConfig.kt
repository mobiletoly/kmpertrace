package dev.goquick.kmpertrace.log

import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.core.LogEvent

/**
 * Global runtime configuration for KmperTrace logging.
 */
object LoggerConfig {

    /**
     * Minimum level allowed through filtering.
     */
    var minLevel: Level = Level.DEBUG

    /**
     * Service name recorded on each event when provided.
     */
    var serviceName: String? = null

    /**
     * Environment name recorded on each event when provided.
     */
    var environment: String? = null

    /**
     * Active backends; empty list disables emission.
     */
    var backends: List<LogBackend> = emptyList()

    /**
     * Whether platform backends should render glyph icons (e.g., ℹ️/⚠️/❌) before lines.
     */
    var renderGlyphs: Boolean = true

    /**
     * Additional filter predicate evaluated before dispatch.
     */
    var filter: (LogEvent) -> Boolean = { event ->
        event.level.ordinal >= minLevel.ordinal
    }
}
