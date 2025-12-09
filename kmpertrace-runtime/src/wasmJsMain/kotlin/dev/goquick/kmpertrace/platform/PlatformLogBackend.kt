@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.goquick.kmpertrace.platform

import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.core.LogEvent
import dev.goquick.kmpertrace.log.LogBackend

@JsFun("line => console.log(line)")
private external fun consoleLog(line: String)

@JsFun("line => console.warn(line)")
private external fun consoleWarn(line: String)

@JsFun("line => console.error(line)")
private external fun consoleError(line: String)

actual object PlatformLogBackend : LogBackend {
    actual override fun log(event: LogEvent) {
        val line = formatLogLine(event)
        when (event.level) {
            Level.ERROR -> consoleError(line)
            Level.WARN -> consoleWarn(line)
            else -> consoleLog(line)
        }
    }
}
