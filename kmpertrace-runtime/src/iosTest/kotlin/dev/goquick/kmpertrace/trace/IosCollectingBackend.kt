package dev.goquick.kmpertrace.trace

import dev.goquick.kmpertrace.core.LogEvent
import dev.goquick.kmpertrace.log.LogBackend

internal class IosCollectingBackend : LogBackend {
    val events = mutableListOf<LogEvent>()
    override fun log(event: LogEvent) {
        events += event
    }
}
