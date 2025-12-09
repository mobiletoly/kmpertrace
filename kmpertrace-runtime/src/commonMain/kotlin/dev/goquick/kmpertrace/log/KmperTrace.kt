package dev.goquick.kmpertrace.log

import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.platform.PlatformLogBackend

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
        backends: List<LogBackend> = listOf(PlatformLogBackend)
    ) {
        LoggerConfig.minLevel = minLevel
        LoggerConfig.serviceName = serviceName
        LoggerConfig.environment = environment
        LoggerConfig.backends = backends
    }
}
