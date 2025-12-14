package dev.goquick.kmpertrace.platform

import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.log.LogRecord
import dev.goquick.kmpertrace.log.LogSink
import dev.goquick.kmpertrace.log.LoggerConfig
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.autoreleasepool
import platform.Foundation.NSLog

actual object PlatformLogSink : LogSink {
    actual override fun emit(record: LogRecord) {
        val formatted = formatForIos(record)
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

    private fun formatForIos(record: LogRecord): String {
        val icon = if (LoggerConfig.renderGlyphs) levelIcon(record.level) + " " else ""
        // Keep a short human prefix (logger + message) without duplicating ts/lvl.
        val human = buildString {
            append(record.tag.ifBlank { "KmperTrace" })
            if (record.message.isNotBlank()) {
                append(": ").append(record.message)
            }
        }
        return "$icon$human ${record.structuredSuffix}"
    }

    internal fun emitPossiblyChunked(line: String) {
        // Preserve real newlines so stack traces stay readable in Xcode console.
        frameIosChunks(line).forEach { chunk ->
            nsLogEscaped(chunk)
        }
    }

    private fun randomChunkId(): String =
        List(8) { CHARS.random() }.joinToString("")

    private const val MAX_LOG_LINE_CHARS = 900
    private const val CHUNK_SIZE = 700
    private val CHARS = "0123456789abcdef".toCharArray()

    internal fun frameIosChunks(
        line: String,
        maxLogLineChars: Int = MAX_LOG_LINE_CHARS,
        chunkSize: Int = CHUNK_SIZE,
        chunkId: String = randomChunkId()
    ): List<String> {
        if (line.length <= maxLogLineChars) return listOf(line)

        val parts = line.chunked(chunkSize)
        return parts.mapIndexed { idx, part ->
            val marker = when (idx) {
                parts.lastIndex -> "($chunkId:kmpert!)"
                else -> "($chunkId:kmpert...)"
            }
            // Marker must be at the very end of the emitted line; aggregator relies on that.
            "$part $marker"
        }
    }
}

internal fun escapeForNsLogFormat(line: String): String =
    line.replace("%", "%%")

@OptIn(BetaInteropApi::class)
private fun nsLogEscaped(line: String) {
    // Kotlin/Native can leak autoreleased Objective-C objects in non-runloop contexts (like tests);
    // be explicit so emitting lots of logs doesn't destabilize the process.
    autoreleasepool {
        // iOS unified logging treats the first argument as a printf-style format string.
        // Escape percent signs so message content doesn't get interpreted as formatting directives.
        NSLog(escapeForNsLogFormat(line))
    }
}
