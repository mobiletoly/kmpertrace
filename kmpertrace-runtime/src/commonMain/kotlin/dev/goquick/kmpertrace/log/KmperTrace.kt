package dev.goquick.kmpertrace.log

import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.platform.PlatformLogSink

/**
 * Entry point for configuring KmperTrace logging.
 */
object KmperTrace {

    /**
     * Configure logging filters, metadata, and backends.
     */
    fun configure(
        minLevel: Level = Level.INFO,
        serviceName: String? = null,
        environment: String? = null,
        sinks: List<LogSink> = listOf(PlatformLogSink),
        filter: (LogRecord) -> Boolean = { true },
        renderGlyphs: Boolean = true,
        emitDebugAttributes: Boolean = false
    ) {
        LoggerConfig.minLevel = minLevel
        LoggerConfig.serviceName = serviceName
        LoggerConfig.environment = environment
        LoggerConfig.sinks = sinks
        LoggerConfig.filter = filter
        LoggerConfig.renderGlyphs = renderGlyphs
        LoggerConfig.emitDebugAttributes = emitDebugAttributes
    }
}
