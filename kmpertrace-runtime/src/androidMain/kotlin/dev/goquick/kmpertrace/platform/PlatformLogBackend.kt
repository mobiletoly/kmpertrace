package dev.goquick.kmpertrace.platform

import android.util.Log as AndroidLog
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.core.LogEvent
import dev.goquick.kmpertrace.log.LogBackend

actual object PlatformLogBackend : LogBackend {
    actual override fun log(event: LogEvent) {
        // Logcat already provides timestamp/level; keep a short human prefix for readability.
        val human = buildString {
            append(event.loggerName.ifEmpty { "KmperTrace" })
            if (event.message.isNotBlank()) {
                append(": ").append(event.message)
            }
        }
        val line = "$human ${formatLogLine(event, includeHumanPrefix = false)}"
        val tag = event.loggerName.ifEmpty { "KmperTrace" }
        when (event.level) {
            Level.VERBOSE -> AndroidLog.v(tag, line, null)
            Level.DEBUG -> AndroidLog.d(tag, line, null)
            Level.INFO -> AndroidLog.i(tag, line, null)
            Level.WARN -> AndroidLog.w(tag, line, null)
            Level.ERROR -> AndroidLog.e(tag, line, null)
            Level.ASSERT -> AndroidLog.wtf(tag, line, null)
        }
    }

    private fun String?.ifEmpty(fallback: String): String = this?.takeIf { it.isNotBlank() } ?: fallback
}
