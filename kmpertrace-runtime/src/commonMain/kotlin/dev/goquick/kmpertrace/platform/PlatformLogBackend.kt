package dev.goquick.kmpertrace.platform

import dev.goquick.kmpertrace.log.LogBackend

/**
 * Default platform backend that renders events to the platform console.
 */
expect object PlatformLogBackend : LogBackend {
    override fun log(event: dev.goquick.kmpertrace.core.LogEvent)
}
