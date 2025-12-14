package dev.goquick.kmpertrace.platform

import android.util.Log as AndroidLog
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.log.LogRecord
import dev.goquick.kmpertrace.log.LogSink

actual object PlatformLogSink : LogSink {
    actual override fun emit(record: LogRecord) {
        // Logcat already provides timestamp/level; keep a short human prefix for readability.
        val tag = record.tag.ifBlank { "KmperTrace" }
        val human = buildString {
            append(tag)
            if (record.message.isNotBlank()) {
                append(": ").append(record.message)
            }
        }
        val line = "$human ${record.structuredSuffix}"

        when (record.level) {
            Level.VERBOSE -> AndroidLog.v(tag, line, null)
            Level.DEBUG -> AndroidLog.d(tag, line, null)
            Level.INFO -> AndroidLog.i(tag, line, null)
            Level.WARN -> AndroidLog.w(tag, line, null)
            Level.ERROR -> AndroidLog.e(tag, line, null)
            Level.ASSERT -> AndroidLog.wtf(tag, line, null)
        }
    }
}
