package dev.goquick.kmpertrace.platform

import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.core.LogEvent
import dev.goquick.kmpertrace.log.LogBackend
import dev.goquick.kmpertrace.log.LoggerConfig
import platform.Foundation.NSLog

actual object PlatformLogBackend : LogBackend {
    actual override fun log(event: LogEvent) {
        val formatted = formatForIos(event)
        emitPossiblyChunked(formatted)
    }

    private fun levelIcon(level: Level): String = when (level) {
        Level.VERBOSE -> "▫️"
        Level.DEBUG -> "🔍"
        Level.INFO -> "ℹ️"
        Level.WARN -> "⚠️"
        Level.ERROR -> "❌"
        Level.ASSERT -> "💥"
    }

    private fun formatForIos(event: LogEvent): String {
        val icon = if (LoggerConfig.renderGlyphs) levelIcon(event.level) + " " else ""
        // Keep a short human prefix (logger + message) without duplicating ts/lvl.
        val human = buildString {
            append(event.loggerName.ifBlank { "KmperTrace" })
            if (event.message.isNotBlank()) {
                append(": ").append(event.message)
            }
        }
        val structured = formatLogLine(event, includeHumanPrefix = false)
        // Use NSLog so simulator/device logs surface via `log stream`.
        // Percent escaping happens in emitPossiblyChunked to avoid double-escaping.
        return "$icon$human $structured"
    }

    private fun emitPossiblyChunked(line: String) {
        // Preserve real newlines so stack traces stay readable in Xcode console.
        if (line.length <= MAX_LOG_LINE_CHARS) {
            NSLog(line.replace("%", "%%"))
            return
        }
        val id = randomChunkId()
        val parts = line.chunked(CHUNK_SIZE)
        parts.forEachIndexed { idx, part ->
            val marker = when (idx) {
                parts.lastIndex -> "($id:kmpert!)"
                else -> "($id:kmpert...)"
            }
            // Emit raw chunk with marker at end; aggregator will stitch before parsing.
            NSLog((part + " " + marker).replace("%", "%%"))
        }
    }

    private fun randomChunkId(): String =
        List(8) { CHARS.random() }.joinToString("")

    private const val MAX_LOG_LINE_CHARS = 900
    private const val CHUNK_SIZE = 700
    private val CHARS = "0123456789abcdef".toCharArray()
}
