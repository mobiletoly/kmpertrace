package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.cli.ansi.AnsiPalette
import dev.goquick.kmpertrace.cli.ansi.maybeColor
import dev.goquick.kmpertrace.cli.ansi.maybeColorBold
import dev.goquick.kmpertrace.cli.ansi.stripAnsi
import dev.goquick.kmpertrace.parse.LogRecordKind
import dev.goquick.kmpertrace.parse.ParsedLogRecord
import dev.goquick.kmpertrace.parse.SpanNode
import dev.goquick.kmpertrace.parse.TraceTree
import java.time.Instant
import java.time.LocalDateTime
import java.time.Year
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField

internal fun renderTraces(
    traces: List<TraceTree>,
    untracedRecords: List<ParsedLogRecord> = emptyList(),
    showSource: Boolean = false,
    maxLineWidth: Int? = null,
    colorize: Boolean = false,
    timeFormat: TimeFormat = TimeFormat.TIME_ONLY,
    zoneId: ZoneId = ZoneId.systemDefault(),
    spanAttrsMode: SpanAttrsMode = SpanAttrsMode.OFF
): String {
    val sb = StringBuilder()

    val timeline = mutableListOf<TimelineItem>()
    val timelineComparator =
        compareBy<TimelineItem> { it.instant == null }
            .thenBy { it.instant }
            .thenBy { it.rawTimestamp == null }
            .thenBy { it.rawTimestamp }
            .thenBy { it.stableOrder }

    val traceAnchors = mutableMapOf<String, TimelineItem>()
    val traceTitles = traces.associate { it.traceId to traceTitle(it, zoneId) }
    val traceLastUpdates = findLatestTraceUpdatesByTraceId(traces, zoneId)

    traces.forEachIndexed { idx, trace ->
        val ts = traceAnchorTimestamp(trace)
        val item =
            TimelineItem(
                instant = parseTimestampToInstant(ts, zoneId),
                rawTimestamp = ts,
                stableOrder = idx,
                kind = TimelineKind.TRACE,
                traceId = trace.traceId
            ) { sbInner ->
                renderTrace(sbInner, trace, showSource, maxLineWidth, colorize, timeFormat, zoneId, spanAttrsMode)
            }
        timeline += item
        traceAnchors[trace.traceId] = item
    }

    untracedRecords.forEachIndexed { idx, record ->
        timeline += TimelineItem(
            instant = parseTimestampToInstant(record.timestamp, zoneId),
            rawTimestamp = record.timestamp,
            stableOrder = traces.size + idx,
            kind = TimelineKind.UNTRACED,
            traceId = null
        ) { sbInner ->
            renderUntraced(sbInner, record, showSource, maxLineWidth, colorize, timeFormat, zoneId)
        }
    }

    // Add at most one marker per trace id (placed at that trace's last `ts`) when the trace had
    // "late updates": some other item (untraced log or other trace block) is chronologically
    // between the trace's anchor and its last update.
    traceLastUpdates.forEach { (traceId, update) ->
        val lastInstant = update.instant ?: return@forEach
        val anchorItem = traceAnchors[traceId] ?: return@forEach
        val anchorInstant = anchorItem.instant ?: return@forEach
        if (!lastInstant.isAfter(anchorInstant)) return@forEach

        val hasInterleavingItem = timeline.any { item ->
            val inst = item.instant ?: return@any false
            if (!inst.isAfter(anchorInstant) || !inst.isBefore(lastInstant)) return@any false
            !(item.kind == TimelineKind.TRACE && item.traceId == traceId)
        }
        if (!hasInterleavingItem) return@forEach

        timeline += TimelineItem(
            instant = lastInstant,
            rawTimestamp = update.rawTimestamp,
            stableOrder = Int.MAX_VALUE - 10_000 + (anchorItem.stableOrder.coerceAtLeast(0) % 10_000),
            kind = TimelineKind.MARKER,
            traceId = traceId
        ) { sbInner ->
            renderUntraced(sbInner, update.toMarkerRecord(traceTitles[traceId]), showSource, maxLineWidth, colorize, timeFormat, zoneId)
        }
    }

    val sorted = timeline.sortedWith(timelineComparator)
    sorted.forEach { item ->
        if (sb.isNotEmpty()) sb.appendLine()
        item.render(sb)
    }

    return sb.toString().trimEnd()
}

private data class LatestTraceUpdate(
    val traceId: String,
    val spanName: String?,
    val rawTimestamp: String?,
    val instant: Instant?
) {
    fun toMarkerRecord(traceTitle: String?): ParsedLogRecord {
        val title = traceTitle?.takeIf { it.isNotBlank() } ?: "trace"
        val spanPart = spanName?.takeIf { it.isNotBlank() }?.let { " / span $it" } ?: ""
        return ParsedLogRecord(
            traceId = "0",
            spanId = "0",
            parentSpanId = null,
            logRecordKind = LogRecordKind.LOG,
            spanName = "-",
            durationMs = null,
            timestamp = rawTimestamp,
            loggerName = "KmperTrace",
            message = "$title ($traceId)$spanPart",
            sourceComponent = null,
            sourceOperation = null,
            sourceLocationHint = null,
            sourceFile = null,
            sourceLine = null,
            sourceFunction = null,
            rawFields = mapOf(
                "lvl" to "info",
                "synthetic" to "trace_update_marker"
            )
        )
    }
}

private enum class TimelineKind { TRACE, UNTRACED, MARKER }

private data class TimelineItem(
    val instant: Instant?,
    val rawTimestamp: String?,
    val stableOrder: Int,
    val kind: TimelineKind,
    val traceId: String?,
    val render: (StringBuilder) -> Unit
)

private fun trimCommonIndent(lines: List<String>): List<String> {
    if (lines.size <= 1) return lines
    val indents = lines.drop(1) // preserve first-line indent
        .filter { it.isNotEmpty() }
        .map { it.takeWhile { ch -> ch == ' ' || ch == '\t' }.length }
    val minIndent = indents.minOrNull() ?: 0
    if (minIndent == 0) return lines
    return listOf(lines.first()) + lines.drop(1).map { line ->
        if (line.length >= minIndent) line.drop(minIndent) else line
    }
}

private fun renderTrace(
    sb: StringBuilder,
    trace: TraceTree,
    showSource: Boolean,
    maxLineWidth: Int?,
    colorize: Boolean,
    timeFormat: TimeFormat,
    zoneId: ZoneId,
    spanAttrsMode: SpanAttrsMode
) {
    val header = "trace ${trace.traceId}"
    sb.append(maybeColor(header, AnsiPalette.header, colorize)).appendLine()
    trace.spans.forEachIndexed { idx, span ->
        renderSpan(
            sb,
            span,
            prefix = "",
            isLast = idx == trace.spans.lastIndex,
            showSource = showSource,
            maxLineWidth = maxLineWidth,
            colorize = colorize,
            timeFormat = timeFormat,
            zoneId = zoneId,
            spanAttrsMode = spanAttrsMode
        )
    }
}

private fun renderUntraced(
    sb: StringBuilder,
    record: ParsedLogRecord,
    showSource: Boolean,
    maxLineWidth: Int?,
    colorize: Boolean,
    timeFormat: TimeFormat,
    zoneId: ZoneId
) {
    if (record.rawFields["synthetic"] == "trace_update_marker") {
        val ts = formatTimestamp(record.timestamp, timeFormat, zoneId)
        val msg = record.message ?: ""
        val prefix = "• 🔄 "
        val continuationPrefix = "   "
        fun maybeMarkerBold(text: String): String =
            if (colorize) "${AnsiPalette.span}${AnsiPalette.marker}$text${AnsiPalette.reset}" else text

        val (tracePart, spanPart) =
            msg.split(" / span ", limit = 2).let { parts ->
                val trace = parts.firstOrNull().orEmpty()
                val span = parts.getOrNull(1)?.let { " / span $it" }.orEmpty()
                trace to span
            }

        val content =
            buildString {
                append(maybeMarkerBold("TRACE UPDATED"))
                append(" at ").append(maybeColor(ts, AnsiPalette.timestamp, colorize))
                append(maybeColor(" → ", AnsiPalette.marker, colorize))
                append(maybeMarkerBold(tracePart))
                if (spanPart.isNotEmpty()) append(maybeColor(spanPart, AnsiPalette.marker, colorize))
            }
        appendWrappedLine(sb, prefix, content, maxLineWidth, continuationPrefix, softWrap = true)
        return
    }

    val ts = formatTimestamp(record.timestamp, timeFormat, zoneId)
    val logger = record.loggerName ?: "-"
    val msg = record.message ?: ""
    val lvl = record.rawFields["lvl"]?.lowercase()
    val isErrorish = record.rawFields["status"] == "ERROR" || record.rawFields["throwable"] != null || lvl == "error" || lvl == "assert"
    val glyph = if (isErrorish) "❌" else levelGlyph(record.rawFields["lvl"])
    val prefix = "• $glyph "
    val continuationPrefix = "   "
    val isRaw = record.rawFields["raw"] == "true"
    val loggerColored = if (isRaw) {
        maybeColor(logger, AnsiPalette.timestamp, colorize)
    } else {
        if (colorize) "${AnsiPalette.span}${AnsiPalette.logger}$logger${AnsiPalette.reset}"
        else logger
    }
    val msgColored = if (isRaw) maybeColor(msg, AnsiPalette.timestamp, colorize) else msg
    val messageLines = trimCommonIndent(msg.split('\n'))
    val firstLineMsg = messageLines.firstOrNull().orEmpty()
    val firstLineColored = if (isErrorish) maybeColor(firstLineMsg, AnsiPalette.error, colorize) else firstLineMsg

    val firstContent = buildString {
        append(maybeColor(ts, AnsiPalette.timestamp, colorize)).append(' ')
        append(loggerColored).append(": ").append(if (isRaw) maybeColor(firstLineColored, AnsiPalette.timestamp, colorize) else firstLineColored)
        if (showSource) {
            val logSource = buildSourceHint(record.sourceComponent, record.sourceOperation, record.sourceLocationHint)
            if (logSource != null) append(" [").append(maybeColor(logSource, AnsiPalette.source, colorize)).append(']')
            val loc = buildLocationSuffix(record.sourceFile, record.sourceLine, record.sourceFunction)
            if (loc != null) append(' ').append(maybeColor(loc, AnsiPalette.location, colorize))
        }
    }
    appendWrappedLine(sb, prefix, firstContent, maxLineWidth, continuationPrefix, softWrap = true)
    if (messageLines.size > 1) {
        messageLines.drop(1).forEach { line ->
            val coloredLine = if (isErrorish) maybeColor(line, AnsiPalette.error, colorize) else if (isRaw) maybeColor(line, AnsiPalette.timestamp, colorize) else line
            appendWrappedLine(sb, continuationPrefix, coloredLine, maxLineWidth, continuationPrefix,
                softWrap = true)
        }
    }
    record.rawFields["stack_trace"]?.let { stack ->
        val decoded = decodeStackTrace(stack)
        val stackLines = decoded.lines().filter { it.isNotEmpty() }
        stackLines.forEach { line ->
            val coloredLine = if (isErrorish) maybeColor(line, AnsiPalette.error, colorize) else line
            appendWrappedLine(sb, continuationPrefix, coloredLine, maxLineWidth, continuationPrefix)
        }
    }
}

private fun traceAnchorTimestamp(trace: TraceTree): String? =
    trace.spans.mapNotNull { it.startTimestamp ?: it.firstTimestamp() }.minOrNull()

private fun traceTitle(trace: TraceTree, zoneId: ZoneId): String? =
    trace.spans
        .minByOrNull { span -> parseTimestampToInstant(span.startTimestamp ?: span.firstTimestamp(), zoneId) ?: Instant.MAX }
        ?.spanName

private fun findLatestTraceUpdatesByTraceId(traces: List<TraceTree>, zoneId: ZoneId): Map<String, LatestTraceUpdate> {
    val bestByTrace = mutableMapOf<String, LatestTraceUpdate>()

    fun consider(traceId: String, spanName: String?, record: ParsedLogRecord) {
        val instant = parseTimestampToInstant(record.timestamp, zoneId) ?: return
        val current = bestByTrace[traceId]
        if (current == null || current.instant == null || instant.isAfter(current.instant)) {
            bestByTrace[traceId] = LatestTraceUpdate(
                traceId = traceId,
                spanName = spanName,
                rawTimestamp = record.timestamp,
                instant = instant
            )
        }
    }

    fun visitSpan(traceId: String, span: SpanNode) {
        span.records.forEach { consider(traceId, span.spanName, it) }
        span.children.forEach { child -> visitSpan(traceId, child) }
    }

    traces.forEach { trace ->
        trace.spans.forEach { span -> visitSpan(trace.traceId, span) }
    }

    return bestByTrace
}

private fun renderSpan(
    sb: StringBuilder,
    span: SpanNode,
    prefix: String,
    isLast: Boolean,
    showSource: Boolean,
    maxLineWidth: Int?,
    colorize: Boolean,
    timeFormat: TimeFormat,
    zoneId: ZoneId,
    spanAttrsMode: SpanAttrsMode
) {
    val connector = if (isLast) "└─" else "├─"
    val spanSourceHint = if (showSource) buildSourceHint(span.sourceComponent, span.sourceOperation, span.sourceLocationHint) else null
    val spanHasError = span.records.any { it.logRecordKind == LogRecordKind.SPAN_END && (it.rawFields["status"] == "ERROR" || it.rawFields["throwable"] != null) }
    val spanPrefix = prefix + connector + ' '
    val continuationPrefixForSpan = prefix + if (isLast) "   " else "│  "
    val journeyHeader = journeyHeader(span)
    val durationPart = if (journeyHeader != null) "" else span.durationMs?.let { " (${it} ms)" } ?: ""
    val spanContent = buildString {
        val nameText = maybeColorBold(span.spanName, colorize = colorize)
        val contentText = if (spanHasError) maybeColor(nameText, AnsiPalette.error, colorize) else nameText
        val glyph = if (spanHasError) "❌ " else ""
        append(glyph).append(contentText)
        append(durationPart)
        journeyHeader?.let { journey ->
            val journeyTs = formatTimestamp(journey.timestamp, timeFormat, zoneId)
            append(" journey ").append(maybeColor("at", AnsiPalette.timestamp, colorize)).append(' ')
            append(maybeColor(journeyTs, AnsiPalette.timestamp, colorize))
            journey.trigger?.takeIf { it.isNotBlank() }?.let { trigger ->
                append(" (trigger=").append(trigger).append(')')
            }
        }
        if (spanSourceHint != null && spanSourceHint != span.spanName) {
            append(" [").append(maybeColor(spanSourceHint, AnsiPalette.source, colorize)).append(']')
        }
        formatSpanAttrs(span.attributes.filteredForJourney(journeyHeader), spanAttrsMode)?.let { attrs ->
            append(' ').append(maybeColor(attrs, AnsiPalette.timestamp, colorize))
        }
    }
    appendWrappedLine(sb, spanPrefix, spanContent, maxLineWidth, continuationPrefixForSpan)

    val childPrefix = prefix + if (isLast) "   " else "│  "

    val items = mutableListOf<SpanRenderable>()
    span.records.forEach { record -> items += SpanRenderable.RecordItem(record) }
    span.children.forEach { child ->
        val childTs = child.startTimestamp ?: child.firstTimestamp()
        items += SpanRenderable.ChildItem(child, childTs)
    }
    val sortedItems = items.sortedWith(compareBy<SpanRenderable> { it.timestamp == null }.thenBy { it.timestamp })

    sortedItems.forEachIndexed { idx, item ->
            val isLastItem = idx == sortedItems.lastIndex
            val connectorItem = if (isLastItem) "└─" else "├─"
            val connectorPad = if (isLastItem) "   " else "│  "
                when (item) {
                    is SpanRenderable.RecordItem -> {
                        val record = item.record
                val ts = formatTimestamp(record.timestamp, timeFormat, zoneId)
                val logger = record.loggerName ?: "-"
                val msg = record.message?.let { massageSpanMarker(record, it) } ?: ""
                val lvl = record.rawFields["lvl"]?.lowercase()
                val isErrorish = record.rawFields["status"] == "ERROR" || record.rawFields["throwable"] != null || lvl == "error" || lvl == "assert"
                        val glyph = if (isErrorish) "❌" else levelGlyph(record.rawFields["lvl"])
                    val logPrefix = "$childPrefix$connectorItem $glyph "
                    val glyphPad = "    " // space + glyph + space + trailing space
                    val continuationPrefixForLog = childPrefix + connectorPad + glyphPad
                val loggerColored = if (colorize) {
                    val code = if (isErrorish) AnsiPalette.error else AnsiPalette.logger
                    "${AnsiPalette.span}$code$logger${AnsiPalette.reset}"
                } else logger
                val messageLines = trimCommonIndent(msg.split('\n'))
                val firstLineMsg = messageLines.firstOrNull().orEmpty()
                val firstLineColored = if (isErrorish) maybeColor(firstLineMsg, AnsiPalette.error, colorize) else firstLineMsg

                    val firstLineContent = buildString {
                        append(maybeColor(ts, AnsiPalette.timestamp, colorize)).append(' ')
                        append(loggerColored).append(": ").append(firstLineColored)
                        if (showSource) {
                            val logSource = buildSourceHint(record.sourceComponent, record.sourceOperation, record.sourceLocationHint)
                            val sourceToShow = if (logSource != null && logSource != spanSourceHint) logSource else null
                            if (sourceToShow != null) append(" [").append(maybeColor(sourceToShow, AnsiPalette.source, colorize)).append(']')
                            val loc = buildLocationSuffix(record.sourceFile, record.sourceLine, record.sourceFunction)
                            if (loc != null) append(' ').append(maybeColor(loc, AnsiPalette.location, colorize))
                        }
                    }
                appendWrappedLine(sb, logPrefix, firstLineContent, maxLineWidth, continuationPrefixForLog,
                    softWrap = true)

                if (messageLines.size > 1) {
                    messageLines.drop(1).forEach { line ->
                        val coloredLine = if (isErrorish) maybeColor(line, AnsiPalette.error, colorize) else line
                        appendWrappedLine(sb, continuationPrefixForLog, coloredLine, maxLineWidth, continuationPrefixForLog,
                            softWrap = true)
                    }
                }

                    record.rawFields["stack_trace"]?.let { stack ->
                        val decoded = decodeStackTrace(stack)
                        val stackLines = decoded.lines().filter { it.isNotEmpty() }
                        val stackPrefix = childPrefix + connectorPad + glyphPad
                        stackLines.forEach { line ->
                            val coloredLine = if (isErrorish) maybeColor(line, AnsiPalette.error, colorize) else line
                            appendWrappedLine(sb, stackPrefix, coloredLine, maxLineWidth, stackPrefix)
                        }
                    }
                }
            is SpanRenderable.ChildItem -> {
                renderSpan(
                    sb,
                    item.child,
                    childPrefix,
                    isLastItem,
                    showSource,
                    maxLineWidth,
                    colorize,
                    timeFormat,
                    zoneId,
                    spanAttrsMode
                )
            }
        }
    }
}

private fun formatSpanAttrs(attributes: Map<String, String>, mode: SpanAttrsMode): String? {
    if (mode == SpanAttrsMode.OFF) return null
    if (attributes.isEmpty()) return null

    val entries = attributes.entries.toList()
    val pairs = entries.joinToString(" ") { (k, v) ->
        "${stripAttrPrefix(k)}=${formatAttrValue(v)}"
    }
    return "{$pairs}"
}

private fun stripAttrPrefix(key: String): String =
    when {
        key.startsWith(DEBUG_ATTRIBUTE_PREFIX) -> DEBUG_DISPLAY_PREFIX + key.removePrefix(DEBUG_ATTRIBUTE_PREFIX)
        key.startsWith(ATTRIBUTE_PREFIX) -> key.removePrefix(ATTRIBUTE_PREFIX)
        else -> key
    }

private fun formatAttrValue(value: String): String {
    val needsQuotes = value.any { it.isWhitespace() } || value.contains('"')
    if (!needsQuotes) return value
    return buildString {
        append('"')
        value.forEach { ch ->
            if (ch == '"') append("\\\"") else append(ch)
        }
        append('"')
    }
}

private const val ATTRIBUTE_PREFIX = "a:"
private const val DEBUG_ATTRIBUTE_PREFIX = "d:"
private const val DEBUG_DISPLAY_PREFIX = "?"

private sealed class SpanRenderable(open val timestamp: String?) {
    data class RecordItem(val record: ParsedLogRecord) : SpanRenderable(record.timestamp)
    data class ChildItem(val child: SpanNode, override val timestamp: String?) : SpanRenderable(timestamp)
}

private fun SpanNode.firstTimestamp(): String? =
    records.mapNotNull { it.timestamp }.minOrNull()
        ?: children.mapNotNull { it.firstTimestamp() }.minOrNull()

private fun Map<String, String>.filteredForJourney(journeyHeader: JourneyHeader?): Map<String, String> {
    if (journeyHeader?.trigger.isNullOrBlank()) return this
    // Avoid visual duplication when we already show `(trigger=...)` as part of the journey header.
    return filterKeys { key -> stripAttrPrefix(key) != "trigger" }
}

private data class JourneyHeader(
    val timestamp: String,
    val trigger: String?
)

private fun journeyHeader(span: SpanNode): JourneyHeader? {
    val isJourneyKind = span.spanKind?.equals("journey", ignoreCase = true) == true
    val trigger = span.attributes["a:trigger"]
    if (isJourneyKind) {
        val ts = span.startTimestamp ?: span.firstTimestamp() ?: return null
        return JourneyHeader(timestamp = ts, trigger = trigger)
    }
    return null
}

private fun buildSourceHint(component: String?, operation: String?, hint: String?): String? =
    when {
        component != null && operation != null -> "$component.$operation"
        component != null -> component
        hint != null -> hint
        else -> null
    }

private fun levelGlyph(level: String?): String = when (level?.lowercase()) {
    "verbose" -> "▫️"
    "debug" -> "🔍"
    "info" -> "ℹ️"
    "warn", "warning" -> "⚠️"
    "error" -> "❌"
    "assert" -> "💥"
    else -> "•"
}

private fun appendWrappedLine(
    sb: StringBuilder,
    prefix: String,
    content: String,
    maxLineWidth: Int?,
    continuationPrefix: String? = null,
    softWrap: Boolean = false
) {
    val prefixVisible = stripAnsi(prefix).length
    if (maxLineWidth == null || maxLineWidth <= prefixVisible) {
        sb.append(prefix).append(content).appendLine()
        return
    }
    val available = maxLineWidth - prefixVisible
    if (stripAnsi(content).length + prefixVisible <= maxLineWidth) {
        sb.append(prefix).append(content).appendLine()
        return
    }

    var remaining = content
    var first = true
    val cont = continuationPrefix ?: " ".repeat(prefixVisible)
    while (stripAnsi(remaining).isNotEmpty()) {
        val chunk = if (softWrap) takeVisiblePreservingWords(remaining, available) else takeVisible(remaining, available)
        val linePrefix = if (first) prefix else cont
        sb.append(linePrefix).append(chunk).appendLine()
        remaining = dropVisible(remaining, stripAnsi(chunk).length)
        first = false
    }
}

private fun takeVisible(text: String, limit: Int): String {
    if (limit <= 0) return ""
    var visibleCount = 0
    val sb = StringBuilder()
    var i = 0
    while (i < text.length && visibleCount < limit) {
        val ch = text[i]
        if (ch == '\u001B') {
            val end = text.indexOf('m', startIndex = i).takeIf { it != -1 } ?: (text.length - 1)
            sb.append(text.substring(i, end + 1))
            i = end + 1
            continue
        }
        sb.append(ch)
        visibleCount++
        i++
    }
    return sb.toString()
}

private fun takeVisiblePreservingWords(text: String, limit: Int): String {
    if (limit <= 0) return ""
    val plain = stripAnsi(text)
    if (plain.length <= limit) return text

    val prevSpace = plain.lastIndexOf(' ', startIndex = (limit - 1).coerceAtLeast(0))
    val nextSpace = plain.indexOf(' ', startIndex = limit).let { if (it == -1) plain.length else it }
    val tokenStart = if (prevSpace == -1) 0 else prevSpace + 1
    val tokenLen = nextSpace - tokenStart

    // If the token would be split and we can keep it intact with a small overflow, do so.
    if (tokenStart == 0 && tokenLen <= limit + WORD_OVERFLOW_BUDGET) {
        return takeVisible(text, tokenLen)
    }
    // If the token straddles the wrap point, prefer breaking before it so it stays intact on next line.
    if (tokenStart in 1 until limit && tokenLen <= limit + WORD_OVERFLOW_BUDGET) {
        return takeVisible(text, tokenStart)
    }

    // Fallback: prefer breaking at the last space before limit; if there are no spaces (URLs/UUIDs),
    // break after a good separator character (/, ?, &, =, -, :, .) to avoid splitting mid-token.
    val rawChunk = takeVisible(text, limit)
    val chunkVisible = stripAnsi(rawChunk)
    val lastSpace = chunkVisible.lastIndexOf(' ')
    val lastSeparator = lastIndexOfAny(chunkVisible, WRAP_SEPARATORS)

    val breakVisibleLen: Int =
        when {
            lastSpace >= MIN_WRAP_BREAK_VISIBLE -> lastSpace
            lastSeparator >= MIN_WRAP_BREAK_VISIBLE -> lastSeparator + 1 // keep the separator at the end of the line
            else -> return rawChunk
        }

    var visibleCount = 0
    var i = 0
    val builder = StringBuilder()
    while (i < rawChunk.length && visibleCount < breakVisibleLen) {
        val ch = rawChunk[i]
        if (ch == '\u001B') {
            val end =
                rawChunk.indexOf('m', startIndex = i).takeIf { it != -1 } ?: (rawChunk.length - 1)
            builder.append(rawChunk.substring(i, end + 1))
            i = end + 1
            continue
        }
        builder.append(ch)
        visibleCount++
        i++
    }
    val trimmed = builder.toString()
    return if (breakVisibleLen == lastSpace) trimmed.trimEnd() else trimmed
}

private fun dropVisible(text: String, visible: Int): String {
    if (visible <= 0) return text
    var remainingVisible = visible
    var i = 0
    while (i < text.length && remainingVisible > 0) {
        val ch = text[i]
        if (ch == '\u001B') {
            val end = text.indexOf('m', startIndex = i).takeIf { it != -1 } ?: (text.length - 1)
            i = end + 1
            continue
        }
        remainingVisible--
        i++
    }
    return if (i >= text.length) "" else text.substring(i)
}

private fun decodeStackTrace(raw: String): String {
    val normalized = raw
        .replace("\\\\n", "\n")
        .replace("\\n", "\n")
        .replace("\r", "")
        .replace("(Explain with AI)", "") // strip Android Studio copy helper
    val lines = normalized
        .lines()
        .dropWhile { it.isBlank() } // drop leading newline we add for readability
    val trimmed = lines.mapIndexed { idx, line ->
        val content = line.trimStart()
        if (idx == 0) content else "    $content"
    }
    return trimmed.joinToString("\n").trimEnd()
}

// Drop span start/end marker heads ("+++ name" / "--- name") from rendering,
// preferring err_msg for span-end errors when present.
private fun massageSpanMarker(record: ParsedLogRecord, msg: String): String {
    if (record.logRecordKind == LogRecordKind.SPAN_START || record.logRecordKind == LogRecordKind.SPAN_END) {
        val trimmed = msg.trim()
        if (trimmed.startsWith("+++ ") || trimmed.startsWith("--- ")) {
            val errMsg = record.rawFields["err_msg"]?.takeIf { it.isNotBlank() }
            if (record.logRecordKind == LogRecordKind.SPAN_END && errMsg != null) return errMsg
            return "" // suppress marker
        }
    }
    return msg
}

private fun lastIndexOfAny(text: String, candidates: Set<Char>): Int {
    for (i in text.length - 1 downTo 0) {
        if (text[i] in candidates) return i
    }
    return -1
}

private val WRAP_SEPARATORS: Set<Char> = setOf('/', '?', '&', '=', '-', ':', '.')
private const val MIN_WRAP_BREAK_VISIBLE = 8

private fun buildLocationSuffix(file: String?, line: Int?, fn: String?): String? {
    if (file == null && line == null && fn == null) return null
    val parts = mutableListOf<String>()
    if (file != null) parts += file
    if (line != null) parts += line.toString()
    val loc = parts.joinToString(":")
    val fnPart = fn?.let { if (loc.isNotEmpty()) "$loc $it" else it } ?: loc
    return "(${fnPart})".takeIf { fnPart.isNotEmpty() }
}

private const val WORD_OVERFLOW_BUDGET = 12

internal fun formatTimestamp(raw: String?, format: TimeFormat): String {
    return formatTimestamp(raw, format, ZoneId.systemDefault())
}

internal fun formatTimestamp(raw: String?, format: TimeFormat, zoneId: ZoneId): String {
    val value = raw ?: return ""

    // ISO-like with 'T'
    val tIdx = value.indexOf('T')
    if (tIdx != -1 && tIdx != value.lastIndex) {
        if (format == TimeFormat.FULL) return value
        // Convert ISO timestamps with Z/offset to local time.
        val hasZone = value.endsWith('Z') || value.indexOfAny(charArrayOf('+', '-'), startIndex = tIdx + 1) != -1
        if (hasZone) {
            val instant = runCatching { Instant.parse(value) }.getOrNull()
            if (instant != null) {
                return DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(zoneId).format(instant)
            }
        }
        // Fallback: best-effort extraction without zone conversion.
        val endIdx = value.indexOfAny(charArrayOf('Z', '+', '-'), startIndex = tIdx + 1).let { if (it == -1) value.length else it }
        val timePortion = value.substring(tIdx + 1, endIdx)
        return normalizeTimePortion(timePortion)
    }

    // Pure epoch seconds (with optional millis), e.g., logcat -v epoch
    if (value.matches(Regex("^\\d{10}(?:\\.\\d+)?$"))) {
        val instant = parseEpochSeconds(value) ?: return value
        return when (format) {
            TimeFormat.FULL -> DateTimeFormatter.ISO_INSTANT.format(instant)
            TimeFormat.TIME_ONLY -> DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(zoneId).format(instant)
        }
    }

    // Logcat month-day format "MM-DD HH:MM:SS.mmm"
    val logcatMatch = Regex("\\d{2}:\\d{2}:\\d{2}\\.\\d+").find(value)
    if (logcatMatch != null) {
        val timePortion = normalizeTimePortion(logcatMatch.value)
        return if (format == TimeFormat.TIME_ONLY) timePortion else value
    }

    // Fallback: return as-is for FULL, or best-effort time extraction for TIME_ONLY (first time-like token)
    return when (format) {
        TimeFormat.FULL -> value
        TimeFormat.TIME_ONLY -> {
            logcatMatch?.value ?: value
        }
    }
}

private fun normalizeTimePortion(timePortion: String): String {
    val dotIdx = timePortion.indexOf('.')
    return if (dotIdx == -1) {
        "$timePortion.000"
    } else buildString {
        append(timePortion.take(dotIdx))
        append('.')
        val frac = timePortion.substring(dotIdx + 1)
        val ms = when {
            frac.length >= 3 -> frac.take(3)
            else -> frac.padEnd(3, '0')
        }
        append(ms)
    }
}

private fun parseEpochSeconds(value: String): Instant? {
    return try {
        val parts = value.split('.', limit = 2)
        val seconds = parts[0].toLong()
        val nanos = if (parts.size == 2) {
            val frac = parts[1].padEnd(3, '0').take(3)
            frac.toLong() * 1_000_000
        } else 0L
        Instant.ofEpochSecond(seconds, nanos)
    } catch (_: Exception) {
        null
    }
}

private fun parseTimestampToInstant(raw: String?, zoneId: ZoneId): Instant? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    // Epoch seconds (with optional millis), e.g., logcat -v epoch
    if (value.matches(Regex("^\\d{10}(?:\\.\\d+)?$"))) {
        return parseEpochSeconds(value)
    }

    // ISO instant with zone/offset (what KmperTrace structured logs emit).
    if (value.indexOf('T') != -1) {
        runCatching { Instant.parse(value) }.getOrNull()?.let { return it }
    }

    // Android Studio style: "yyyy-MM-dd HH:mm:ss.SSS" (assume local zone)
    Regex("^\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+$").matchEntire(value)?.let {
        val normalized = value.replace(' ', 'T')
        val ldt = runCatching { LocalDateTime.parse(normalized) }.getOrNull()
        return ldt?.atZone(zoneId)?.toInstant()
    }

    // iOS syslog style: "yyyy-MM-dd HH:mm:ss.SSSSSS-0500"
    Regex("^\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+(?:[+-]\\d{4})$").matchEntire(value)?.let {
        val normalized = value.replace(' ', 'T')
        val fmt = DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .appendOffset("+HHmm", "Z")
            .toFormatter()
        return runCatching { ZonedDateTime.parse(normalized, fmt).toInstant() }.getOrNull()
    }

    // Logcat month-day format "MM-DD HH:mm:ss.SSS" (assume current year + local zone)
    Regex("^(\\d{2})-(\\d{2})\\s+(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d+)$").matchEntire(value)?.let { m ->
        val year = Year.now(zoneId).value
        val month = m.groupValues[1].toInt()
        val day = m.groupValues[2].toInt()
        val hour = m.groupValues[3].toInt()
        val minute = m.groupValues[4].toInt()
        val second = m.groupValues[5].toInt()
        val ms = m.groupValues[6].padEnd(3, '0').take(3).toInt()
        return runCatching {
            LocalDateTime.of(year, month, day, hour, minute, second, ms * 1_000_000).atZone(zoneId).toInstant()
        }.getOrNull()
    }

    return null
}
