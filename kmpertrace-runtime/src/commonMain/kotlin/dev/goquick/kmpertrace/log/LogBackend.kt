package dev.goquick.kmpertrace.log

import dev.goquick.kmpertrace.core.LogEvent

/**
 * Pluggable sink for structured log events.
 */
interface LogBackend {
    /**
     * Persist or forward the given event to the underlying destination.
     */
    fun log(event: LogEvent)
}
