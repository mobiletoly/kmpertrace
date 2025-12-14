package dev.goquick.kmpertrace.log

import dev.goquick.kmpertrace.core.Level
import kotlin.time.Instant

/**
 * Public sink API for receiving KmperTrace output.
 *
 * The stable contract is the rendered structured suffix (`|{ ... }|`) and/or the full rendered line.
 * The internal structured record model is intentionally not exposed.
 */
fun interface LogSink {
    fun emit(record: LogRecord)
}

/**
 * A rendered log record.
 *
 * - [structuredSuffix] always contains the `|{ ... }|` wrapper and is intended to be parseable.
 * - [line] is a default human-friendly line that includes [structuredSuffix].
 * - Platform sinks may choose to ignore [line] and reformat using other fields.
 */
data class LogRecord(
    val timestamp: Instant,
    val level: Level,
    val tag: String,
    val message: String,
    val line: String,
    val structuredSuffix: String
)
