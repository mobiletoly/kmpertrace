@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.goquick.kmpertrace.platform

import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.log.LogRecord
import dev.goquick.kmpertrace.log.LogSink

@JsFun("line => console.log(line)")
private external fun consoleLog(line: String)

@JsFun("line => console.warn(line)")
private external fun consoleWarn(line: String)

@JsFun("line => console.error(line)")
private external fun consoleError(line: String)

actual object PlatformLogSink : LogSink {
    actual override fun emit(record: LogRecord) {
        val line = record.line
        when (record.level) {
            Level.ERROR -> consoleError(line)
            Level.WARN -> consoleWarn(line)
            else -> consoleLog(line)
        }
    }
}
