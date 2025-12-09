package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.parse.ParsedEvent
import dev.goquick.kmpertrace.parse.parseLine
import dev.goquick.kmpertrace.parse.EventKind

enum class RawLogLevel { OFF, ALL, VERBOSE, DEBUG, INFO, WARN, ERROR, ASSERT }

internal data class RawLogParseResult(
    val level: RawLogLevel,
    val logger: String?,
    val message: String,
    val timestamp: String?,
    val rawFields: Map<String, String> = emptyMap()
)

internal interface RawLogParser {
    /**
     * Attempt to parse a raw log line. Return null if this parser does not handle the format.
     */
    fun parse(line: String): RawLogParseResult?
}

internal fun validateRawLevel(value: String) {
    parseRawLevel(value) // will throw if invalid
}

internal fun parseRawLevel(value: String?): RawLogLevel =
    when (value?.lowercase()) {
        null, "off" -> RawLogLevel.OFF
        "all" -> RawLogLevel.ALL
        "verbose", "v" -> RawLogLevel.VERBOSE
        "debug", "d" -> RawLogLevel.DEBUG
        "info", "i" -> RawLogLevel.INFO
        "warn", "warning", "w" -> RawLogLevel.WARN
        "error", "e" -> RawLogLevel.ERROR
        "assert", "a", "fatal" -> RawLogLevel.ASSERT
        else -> throw IllegalArgumentException("Invalid --raw-logs value: $value (use off|all|verbose|debug|info|warn|error|assert)")
    }

internal fun parseLinesWithRaw(
    lines: Sequence<String>,
    rawLevel: RawLogLevel
): Pair<List<dev.goquick.kmpertrace.parse.ParsedEvent>, List<dev.goquick.kmpertrace.parse.ParsedEvent>> {
    val structured = mutableListOf<dev.goquick.kmpertrace.parse.ParsedEvent>()
    val raw = mutableListOf<dev.goquick.kmpertrace.parse.ParsedEvent>()
    lines.forEach { line ->
        val evt = parseLine(line)
        if (evt != null) {
            structured += evt
        } else if (rawLevel != RawLogLevel.OFF) {
            rawEventFromLine(line, rawLevel)?.let { raw += it }
        }
    }
    return structured to raw
}

internal fun rawEventFromLine(line: String, minLevel: RawLogLevel): ParsedEvent? {
    // Skip if it's a structured KmperTrace line.
    if (parseLine(line) != null) return null
    if (isNoiseLine(line)) return null
    val trimmed = line.trimEnd()
    if (trimmed.contains("|{ ts=") && trimmed.endsWith("}|")) return null // structured suffix pattern
    if (line.contains("|{")) return null // structured line; skip duplicating in raw view
    val parsed = dispatchParsers(line) ?: return null
    if (!levelAllows(parsed.level, minLevel)) return null
    return ParsedEvent(
        traceId = "0",
        spanId = "0",
        parentSpanId = null,
        eventKind = EventKind.LOG,
        spanName = "-",
        durationMs = null,
        loggerName = parsed.logger,
        timestamp = parsed.timestamp,
        message = parsed.message,
        sourceComponent = null,
        sourceOperation = null,
        sourceLocationHint = null,
        sourceFile = null,
        sourceLine = null,
        sourceFunction = null,
        rawFields = parsed.rawFields.toMutableMap().apply {
            put("lvl", parsed.level.name.lowercase())
            put("raw", "true")
        }
    )
}

private fun levelAllows(actual: RawLogLevel, min: RawLogLevel): Boolean {
    if (min == RawLogLevel.OFF) return false
    if (min == RawLogLevel.ALL) return true
    if (actual == RawLogLevel.ALL) return true // unknown/unspecified level → show it
    return actual.ordinal >= min.ordinal
}

private fun isNoiseLine(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.startsWith("--------- beginning of")) return true
    if (trimmed.contains("logcat for pid=")) return true
    if (trimmed.startsWith("pid ") && trimmed.contains("exited; restarting when app returns")) return true
    if (trimmed.startsWith("waiting for ") && trimmed.contains("to start")) return true
    return false
}

private val parsers: List<RawLogParser> = listOf(
    AndroidLogcatParser,
    IosUnifiedLogParser,
    GenericRawParser
)

private fun dispatchParsers(line: String): RawLogParseResult? {
    parsers.forEach { parser ->
        val parsed = parser.parse(line)
        if (parsed != null) return parsed
    }
    return null
}

/**
 * Parses logcat lines produced with `adb logcat -v epoch --pid=...` and similar space-separated formats.
 */
private object AndroidLogcatParser : RawLogParser {
    private val epochRegex =
        Regex("^\\s*(\\d+\\.\\d+)\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEFA])\\s+([^:]+):\\s*(.*)$")
    private val monthDayRegex =
        Regex("^\\s*(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+)\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEFA])\\s+([^:]+):\\s*(.*)$")
    private val studioRegex =
        // 2025-12-09 01:29:53.305  5510-5510  ziparchive  dev.goquick...  W  message
        Regex("^\\s*(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+)\\s+\\d+[- ]+\\d+\\s+(\\S+)\\s+\\S+\\s+([VDIWEFA])\\s+(.*)$")

    override fun parse(line: String): RawLogParseResult? {
        val match = epochRegex.find(line)
            ?: monthDayRegex.find(line)
            ?: studioRegex.find(line)
            ?: return null

        val ts = match.groupValues[1]
        val levelToken = match.groupValues[studioRegex.find(line)?.let { 3 } ?: 4]
        val loggerToken = match.groupValues[studioRegex.find(line)?.let { 2 } ?: 5].trim()
        val messageToken = match.groupValues[studioRegex.find(line)?.let { 4 } ?: 6]

        val level = mapLevel(levelToken) ?: return null
        return RawLogParseResult(
            level = level,
            logger = loggerToken,
            message = messageToken.trimEnd(),
            timestamp = ts,
        )
    }

    private fun mapLevel(token: String): RawLogLevel? = when (token.uppercase()) {
        "V" -> RawLogLevel.VERBOSE
        "D" -> RawLogLevel.DEBUG
        "I" -> RawLogLevel.INFO
        "W" -> RawLogLevel.WARN
        "E" -> RawLogLevel.ERROR
        "F", "A" -> RawLogLevel.ASSERT
        else -> null
    }
}

/**
 * Parses iOS/macOS unified logging output as produced by `log stream` / `log show` (syslog or compact styles).
 */
private object IosUnifiedLogParser : RawLogParser {
    // Examples:
    // 2025-12-08 23:18:36.143963-0500  localhost powerd[333]: [com.apple.powerd:displayState] DesktopMode check on Battery 0
    // 12:34:56.789 MyApp[123:4567] <Info> [com.company:net] Fetching...
    private val syslogHead = Regex(
        "^\\s*(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+(?:[+-]\\d{4})?)\\s+(?:\\S+\\s+)?([^\\[]+)\\[(\\d+)(?::(\\d+))?]:\\s*(.*)$"
    )
    private val compactHead = Regex(
        "^\\s*(\\d{2}:\\d{2}:\\d{2}\\.\\d+)\\s+([^\\[]+)\\[(\\d+)(?::(\\d+))?]\\s*(.*)$"
    )

    override fun parse(line: String): RawLogParseResult? {
        val head = syslogHead.find(line) ?: compactHead.find(line) ?: return null
        val ts = head.groupValues[1]
        val process = head.groupValues[2].trim()
        val pid = head.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
        val tid = head.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }
        var remainder = head.groupValues[5].trim()

        var subsystem: String? = null
        if (remainder.startsWith("[")) {
            val end = remainder.indexOf(']')
            if (end > 0) {
                subsystem = remainder.substring(1, end).trim().ifEmpty { null }
                remainder = remainder.substring(end + 1).trimStart()
                if (remainder.startsWith(":")) {
                    remainder = remainder.substring(1).trimStart()
                }
            }
        }

        var level = RawLogLevel.INFO
        if (remainder.startsWith("<")) {
            val end = remainder.indexOf('>')
            if (end > 0) {
                val token = remainder.substring(1, end)
                level = mapUnifiedLevel(token)
                remainder = remainder.substring(end + 1).trimStart()
            }
        }

        if (subsystem == null && remainder.startsWith("[")) {
            val end = remainder.indexOf(']')
            if (end > 0) {
                subsystem = remainder.substring(1, end).trim().ifEmpty { null }
                remainder = remainder.substring(end + 1).trimStart()
                if (remainder.startsWith(":")) {
                    remainder = remainder.substring(1).trimStart()
                }
            }
        }

        val message = remainder.trimEnd()
        val rawFields = mutableMapOf<String, String>()
        subsystem?.let { rawFields["subsystem"] = it }
        pid?.let { rawFields["pid"] = it }
        tid?.let { rawFields["tid"] = it }
        return RawLogParseResult(
            level = level,
            logger = process,
            message = message,
            timestamp = ts,
            rawFields = rawFields
        )
    }

    private fun mapUnifiedLevel(token: String): RawLogLevel = when (token.lowercase()) {
        "debug" -> RawLogLevel.DEBUG
        "info", "default" -> RawLogLevel.INFO
        "notice" -> RawLogLevel.INFO
        "error" -> RawLogLevel.ERROR
        "fault" -> RawLogLevel.ASSERT
        "critical" -> RawLogLevel.ASSERT
        else -> RawLogLevel.ALL
    }
}

private object GenericRawParser : RawLogParser {
    private val levelRegex =
        Regex("\\b(VERBOSE|DEBUG|INFO|WARN|WARNING|ERROR|ASSERT|FATAL)\\b", RegexOption.IGNORE_CASE)
    private val logcatLevelRegex = Regex("\\b([VDIWEF])\\/[^:]*:")
    private val isoTsRegex = Regex("\\d{4}-\\d{2}-\\d{2}T\\S+")
    private val logcatTsRegex = Regex("\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+")

    override fun parse(line: String): RawLogParseResult? {
        val level = detectLevel(line) ?: RawLogLevel.ALL
        val ts = detectTimestamp(line)
        val logger = detectLogger(line)
        return RawLogParseResult(
            level = level,
            logger = logger,
            message = line.trimEnd(),
            timestamp = ts
        )
    }

    private fun detectLevel(line: String): RawLogLevel? {
        logcatLevelRegex.find(line)?.let { m ->
            return when (m.groupValues[1].uppercase()) {
                "V" -> RawLogLevel.VERBOSE
                "D" -> RawLogLevel.DEBUG
                "I" -> RawLogLevel.INFO
                "W" -> RawLogLevel.WARN
                "E" -> RawLogLevel.ERROR
                "F" -> RawLogLevel.ASSERT
                else -> null
            }
        }
        levelRegex.find(line)?.let { m ->
            return when (m.groupValues[1].lowercase()) {
                "verbose" -> RawLogLevel.VERBOSE
                "debug" -> RawLogLevel.DEBUG
                "info" -> RawLogLevel.INFO
                "warn", "warning" -> RawLogLevel.WARN
                "error" -> RawLogLevel.ERROR
                "assert", "fatal" -> RawLogLevel.ASSERT
                else -> null
            }
        }
        return null
    }

    private fun detectTimestamp(line: String): String? =
        isoTsRegex.find(line)?.value ?: logcatTsRegex.find(line)?.value

    private fun detectLogger(line: String): String? {
        // try "LoggerName: message"
        val colon = line.indexOf(':')
        if (colon > 0) {
            val candidate = line.substring(0, colon).trim()
            if (candidate.isNotEmpty() && !candidate.contains(' ')) return candidate
        }
        return null
    }
}

private val levelRegex = Regex("\\b(VERBOSE|DEBUG|INFO|WARN|WARNING|ERROR|ASSERT|FATAL)\\b", RegexOption.IGNORE_CASE)
private val logcatLevelRegex = Regex("\\b([VDIWEF])\\/[^:]*:")

private fun detectLevel(line: String): RawLogLevel? {
    logcatLevelRegex.find(line)?.let { m ->
        return when (m.groupValues[1].uppercase()) {
            "V" -> RawLogLevel.VERBOSE
            "D" -> RawLogLevel.DEBUG
            "I" -> RawLogLevel.INFO
            "W" -> RawLogLevel.WARN
            "E" -> RawLogLevel.ERROR
            "F" -> RawLogLevel.ASSERT
            else -> null
        }
    }
    levelRegex.find(line)?.let { m ->
        return when (m.groupValues[1].lowercase()) {
            "verbose" -> RawLogLevel.VERBOSE
            "debug" -> RawLogLevel.DEBUG
            "info" -> RawLogLevel.INFO
            "warn", "warning" -> RawLogLevel.WARN
            "error" -> RawLogLevel.ERROR
            "assert", "fatal" -> RawLogLevel.ASSERT
            else -> null
        }
    }
    return null
}

private val isoTsRegex = Regex("\\d{4}-\\d{2}-\\d{2}T\\S+")
private val logcatTsRegex = Regex("\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+")

private fun detectTimestamp(line: String): String? =
    isoTsRegex.find(line)?.value ?: logcatTsRegex.find(line)?.value

private fun detectLogger(line: String): String? {
    // try "LoggerName: message"
    val colon = line.indexOf(':')
    if (colon > 0) {
        val candidate = line.substring(0, colon).trim()
        if (candidate.isNotEmpty() && !candidate.contains(' ')) return candidate
    }
    return null
}
