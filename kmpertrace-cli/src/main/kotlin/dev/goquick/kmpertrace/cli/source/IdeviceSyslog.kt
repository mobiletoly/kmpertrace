package dev.goquick.kmpertrace.cli.source

import java.time.Year
import java.time.ZoneId

/**
 * `idevicesyslog` prefixes every line with a human-readable syslog-style header, e.g.
 *
 * `Dec 15 01:39:46.970278 SampleApp(SampleApp.debug.dylib)[10612] <Notice>: <payload>`
 *
 * For KmperTrace parsing we want to work with the original payload (especially for multiline
 * structured suffix entries where stack traces may span multiple physical lines).
 */
internal object IdeviceSyslogPrefixStripper {
    private val idevicePrefix =
        Regex(
            "^\\s*([A-Z][a-z]{2})\\s+(\\d{1,2})\\s+(\\d{2}:\\d{2}:\\d{2}\\.\\d{6})\\s+([^\\[]+?)\\[(\\d+)\\]\\s+<([^>]+)>:\\s+(.*)$"
        )

    data class Header(
        val processAndSubsystem: String,
        val pid: String,
        val level: String
    )

    fun hasPrefix(line: String): Boolean = idevicePrefix.containsMatchIn(line)

    fun parseHeader(line: String): Header? =
        idevicePrefix.matchEntire(line)?.let { m ->
            Header(
                processAndSubsystem = m.groupValues[4].trim(),
                pid = m.groupValues[5].trim(),
                level = m.groupValues[6].trim()
            )
        }

    fun stripToPayload(line: String): String =
        idevicePrefix.matchEntire(line)?.groupValues?.getOrNull(7) ?: line

    /**
     * Convert idevicesyslog headers to an iOS/macOS `log stream --style syslog`-like format so our
     * existing raw-log parser can extract timestamps/logger/level.
     *
     * Output example:
     * `2025-12-15 11:48:28.972318 SampleApp[11710]: <Notice> <payload>`
     */
    fun normalizeToSyslogStyle(line: String, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val m = idevicePrefix.matchEntire(line) ?: return line
        val month = monthNumber(m.groupValues[1]) ?: return line
        val day = m.groupValues[2].toIntOrNull() ?: return line
        val time = m.groupValues[3]
        val processAndSubsystem = m.groupValues[4].trim()
        val pid = m.groupValues[5]
        val level = m.groupValues[6].trim()
        val payload = m.groupValues[7]

        val process = processAndSubsystem.substringBefore('(').trim().ifEmpty { processAndSubsystem.trim() }
        val subsystem = processAndSubsystem.substringAfter('(', "").substringBefore(')', "").trim().ifEmpty { null }
        val year = Year.now(zoneId).value

        val datePrefix = "%04d-%02d-%02d %s".format(year, month, day, time)
        val subsystemPrefix = subsystem?.let { "[$it] " } ?: ""
        return "$datePrefix $process[$pid]: $subsystemPrefix<$level> $payload"
    }

    private fun monthNumber(token: String): Int? =
        when (token.lowercase()) {
            "jan" -> 1
            "feb" -> 2
            "mar" -> 3
            "apr" -> 4
            "may" -> 5
            "jun" -> 6
            "jul" -> 7
            "aug" -> 8
            "sep" -> 9
            "oct" -> 10
            "nov" -> 11
            "dec" -> 12
            else -> null
        }
}

/**
 * Device (idevicesyslog) stream filtering strategy:
 * - Always optionally filter by app process token (`<proc>(...)`), if provided.
 * - When raw logs are disabled, keep only KmperTrace structured records, but preserve multiline
 *   structured frames (stack traces) by retaining lines while inside an open `|{ ... }|` frame.
 * - Always strip idevicesyslog prefixes on retained lines before downstream parsing.
 */
internal class IdeviceSyslogLineProcessor(
    iosProc: String?
) {
    private val procToken = iosProc?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it(" }
    private var inStructuredFrame = false
    private var structuredHeader: IdeviceSyslogPrefixStripper.Header? = null

    fun process(line: String): String? {
        val matchesProc = procToken?.let { line.contains(it) } ?: true
        if (!matchesProc && !inStructuredFrame) return null
        if (!matchesProc && inStructuredFrame && IdeviceSyslogPrefixStripper.hasPrefix(line)) {
            // Another process log interleaved while we were buffering a multiline record.
            return null
        }

        val hasPrefix = IdeviceSyslogPrefixStripper.hasPrefix(line)
        val header = if (hasPrefix) IdeviceSyslogPrefixStripper.parseHeader(line) else null
        val payloadOnly = if (hasPrefix) IdeviceSyslogPrefixStripper.stripToPayload(line) else line

        val startsStructured = payloadOnly.contains("|{")
        val closesStructured = payloadOnly.contains("}|")
        val looksLikeKmperTrace = hasPrefix && looksLikeKmperTraceStart(payloadOnly)

        val justStartedStructured = !inStructuredFrame && (startsStructured || looksLikeKmperTrace)

        if (justStartedStructured) {
            inStructuredFrame = true
            structuredHeader = header
        } else if (inStructuredFrame) {
            val currentHeader = structuredHeader
            val matchesStructuredHeader =
                if (!hasPrefix) true
                else header != null &&
                    currentHeader != null &&
                    header.processAndSubsystem == currentHeader.processAndSubsystem &&
                    header.pid == currentHeader.pid &&
                    header.level == currentHeader.level
            if (hasPrefix && !matchesStructuredHeader) {
                return null
            }
        }

        val isStructuredContinuation =
            inStructuredFrame && !payloadOnly.contains("|{") && !justStartedStructured

        if (inStructuredFrame && closesStructured) {
            inStructuredFrame = false
            structuredHeader = null
        }

        // For raw log lines we want timestamps/logger/level (normalize prefix).
        // For multiline structured frames we must keep continuation lines prefix-less, otherwise the
        // syslog header becomes part of quoted fields like `stack_trace`.
        return when {
            hasPrefix && isStructuredContinuation && looksLikeNewLogPrefix(payloadOnly) -> null
            hasPrefix && isStructuredContinuation -> payloadOnly
            hasPrefix -> IdeviceSyslogPrefixStripper.normalizeToSyslogStyle(line)
            else -> line
        }
    }

    private fun looksLikeKmperTraceStart(payload: String): Boolean {
        val trimmed = payload.trimStart()
        val withoutGlyph = trimmed.removeLeadingGlyph()
        if (withoutGlyph.startsWith("+++ ") || withoutGlyph.startsWith("--- ")) return true
        val colon = withoutGlyph.indexOf(':')
        if (colon <= 0) return false
        val logger = withoutGlyph.substring(0, colon)
        return !logger.contains(' ')
    }

    private fun looksLikeNewLogPrefix(payload: String): Boolean {
        val trimmed = payload.trimStart()
        val withoutGlyph = trimmed.removeLeadingGlyph()
        val stackLike = listOf("at ", "Caused by:", "Suppressed:", "...")
        if (stackLike.any(withoutGlyph::startsWith)) return false
        if (withoutGlyph.startsWith("+++ ") || withoutGlyph.startsWith("--- ")) return true
        val colon = withoutGlyph.indexOf(':')
        if (colon <= 0) return false
        val logger = withoutGlyph.substring(0, colon)
        return !logger.contains(' ')
    }
}

private fun String.removeLeadingGlyph(): String {
    val glyphs = listOf("▫️", "🔍", "ℹ️", "⚠️", "❌", "💥")
    var out = this
    for (glyph in glyphs) {
        if (out.startsWith(glyph)) {
            return out.removePrefix(glyph).trimStart()
        }
    }
    return out
}
