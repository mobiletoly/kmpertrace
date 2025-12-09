package dev.goquick.kmpertrace.platform

import dev.goquick.kmpertrace.core.LogEvent
import dev.goquick.kmpertrace.log.LogBackend

actual object PlatformLogBackend : LogBackend {
    actual override fun log(event: LogEvent) {
        println(formatLogLine(event))
    }
}
