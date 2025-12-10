package dev.goquick.kmpertrace.parse

/**
 * Parse a single log line. Returns null when the line does not contain a valid
 * structured KmperTrace suffix (no pipe, missing trace_id or ev).
 */
fun parseLine(line: String): ParsedEvent? {
    val trimmed = line.trimEnd()
    val start = trimmed.lastIndexOf("|{")
    val end = trimmed.lastIndexOf("}|")
    if (start == -1 || end == -1 || end <= start + 2) return null
    val humanPrefix = trimmed.substring(0, start).trimEnd()

    val structured = trimmed.substring(start + 2, end).trim()
    if (structured.isEmpty()) return null

    val fields = normalizeFields(parseLogfmt(structured).toMutableMap())
    if (!(fields.containsKey("ts") && fields.containsKey("lvl"))) {
        return null
    }
    val traceId = fields["trace"] ?: "0"
    val eventKindStr = fields["ev"]
    val eventKind = if (eventKindStr == null) {
        EventKind.LOG
    } else {
        runCatching { EventKind.valueOf(eventKindStr) }.getOrNull() ?: return null
    }

    val spanId = fields["span"] ?: "0"
    val parentSpanIdRaw = fields["parent"]
    val parentSpanId = parentSpanIdRaw?.takeUnless { it == "0" || it == "-" }
    val spanName = fields["name"]
    val durationMs = fields["dur"]?.toLongOrNull()
    val human = parseHumanPrefix(humanPrefix)
    val loggerName = fields["log"] ?: fields["src_comp"] ?: fields["src_hint"] ?: human.logger
    val timestamp = fields["ts"]
    val head = fields["head"]
    val message = resolveMessage(loggerName, head, humanPrefix, human)
    val sourceComponent = fields["src_comp"]
    val sourceOperation = fields["src_op"]
    val sourceLocationHint = fields["src_hint"]
    val sourceFile = fields["file"]
    val sourceLine = fields["line"]?.toIntOrNull()
    val sourceFunction = fields["fn"]

    return ParsedEvent(
        traceId = traceId,
        spanId = spanId,
        parentSpanId = parentSpanId,
        eventKind = eventKind,
        spanName = spanName,
        durationMs = durationMs,
        loggerName = loggerName,
        timestamp = timestamp,
        message = message,
        sourceComponent = sourceComponent,
        sourceOperation = sourceOperation,
        sourceLocationHint = sourceLocationHint,
        sourceFile = sourceFile,
        sourceLine = sourceLine,
        sourceFunction = sourceFunction,
        rawFields = fields
    )
}

/**
 * Parse multiple lines; drops lines that cannot be parsed.
 */
fun parseLines(lines: Sequence<String>): List<ParsedEvent> =
    coalesceWrappedLines(lines).mapNotNull(::parseLine).toList()

/**
 * Parse multiple lines provided via [Iterable]; drops lines that cannot be parsed.
 */
fun parseLines(lines: Iterable<String>): List<ParsedEvent> =
    coalesceWrappedLines(lines.asSequence()).mapNotNull(::parseLine).toList()

/**
 * Build trace trees from parsed events. Non-traced events (trace_id == "0") are
 * excluded from TraceTrees but can still be kept in ParsedEvent lists if desired upstream.
 */
fun buildTraces(events: List<ParsedEvent>): List<TraceTree> {
    val traces = mutableListOf<TraceTree>()

    events.groupBy { it.traceId }
        .filterKeys { it != "0" }
        .forEach { (traceId, traceEvents) ->
            val spanBuilders = linkedMapOf<String, SpanBuilder>()

            traceEvents.forEachIndexed { idx, event ->
                val builder = spanBuilders.getOrPut(event.spanId) { SpanBuilder() }
                if (builder.firstSeenIndex == null) builder.firstSeenIndex = idx
                if (event.timestamp != null) {
                    if (builder.startTimestamp == null || event.eventKind == EventKind.SPAN_START) {
                        builder.startTimestamp = event.timestamp
                    }
                }
                if (builder.parentSpanId == null && event.parentSpanId != null) {
                    builder.parentSpanId = event.parentSpanId
                }
                if (builder.spanName == null && event.spanName != null) {
                    builder.spanName = event.spanName
                }
                if (event.eventKind == EventKind.SPAN_END && event.durationMs != null) {
                    builder.durationMs = event.durationMs
                } else if (builder.durationMs == null && event.durationMs != null) {
                    builder.durationMs = event.durationMs
                }
                if (builder.sourceComponent == null && event.sourceComponent != null) builder.sourceComponent = event.sourceComponent
                if (builder.sourceOperation == null && event.sourceOperation != null) builder.sourceOperation = event.sourceOperation
                if (builder.sourceLocationHint == null && event.sourceLocationHint != null) builder.sourceLocationHint = event.sourceLocationHint
                if (builder.sourceFile == null && event.sourceFile != null) builder.sourceFile = event.sourceFile
                if (builder.sourceLine == null && event.sourceLine != null) builder.sourceLine = event.sourceLine
                if (builder.sourceFunction == null && event.sourceFunction != null) builder.sourceFunction = event.sourceFunction
                if (event.eventKind == EventKind.LOG) {
                    builder.logs.add(event)
                } else if (event.eventKind == EventKind.SPAN_END && event.rawFields.containsKey("stack_trace")) {
                    // Surface span-end errors (with stack traces) alongside logs for rendering.
                    builder.logs.add(event)
                }
            }

            val nodes = spanBuilders.mapValues { (spanId, b) ->
                MutableSpanNode(
                    spanId = spanId,
                    parentSpanId = b.parentSpanId,
                    spanName = b.spanName ?: spanId,
                    durationMs = b.durationMs,
                    startTimestamp = b.startTimestamp,
                    sourceComponent = b.sourceComponent,
                    sourceOperation = b.sourceOperation,
                    sourceLocationHint = b.sourceLocationHint,
                    sourceFile = b.sourceFile,
                    sourceLine = b.sourceLine,
                    sourceFunction = b.sourceFunction,
                    events = b.logs.toList(),
                    firstSeenIndex = b.firstSeenIndex ?: Int.MAX_VALUE
                )
            }

            val roots = mutableListOf<MutableSpanNode>()
            nodes.values.forEach { node ->
                val parentId = node.parentSpanId
                if (parentId != null) {
                    val parent = nodes[parentId]
                    if (parent != null) {
                        parent.children.add(node)
                    } else {
                        roots.add(node)
                    }
                } else {
                    roots.add(node)
                }
            }

            roots.sortWith(spanOrderComparator)
            roots.forEach { sortChildren(it) }
            traces += TraceTree(
                traceId = traceId,
                spans = roots.map { it.toSpanNode() }
            )
        }

    return traces
}

/**
 * Logcat may wrap long lines, splitting the human-readable prefix from the structured suffix.
 * Coalesce consecutive lines until we see a suffix terminator (`}|`), joining with spaces.
 */
private fun coalesceWrappedLines(lines: Sequence<String>): Sequence<String> = sequence {
    val pending = mutableListOf<String>()
    for (line in lines) {
        pending.add(line)
        val joined = pending.joinToString("\n") { it }

        // Heuristic: emit when we see a closing structured suffix.
        if (joined.contains("}|")) {
            yield(joined)
            pending.clear()
        }
    }
    if (pending.isNotEmpty()) {
        // emit remainder as-is if not a complete structured line
        pending.forEach { yield(it) }
    }
}

private data class SpanBuilder(
    var parentSpanId: String? = null,
    var spanName: String? = null,
    var durationMs: Long? = null,
    var sourceComponent: String? = null,
    var sourceOperation: String? = null,
    var sourceLocationHint: String? = null,
    var sourceFile: String? = null,
    var sourceLine: Int? = null,
    var sourceFunction: String? = null,
    var firstSeenIndex: Int? = null,
    var startTimestamp: String? = null,
    val logs: MutableList<ParsedEvent> = mutableListOf()
)

private class MutableSpanNode(
    val spanId: String,
    val parentSpanId: String?,
    val spanName: String,
    val durationMs: Long?,
    val startTimestamp: String?,
    val sourceComponent: String?,
    val sourceOperation: String?,
    val sourceLocationHint: String?,
    val sourceFile: String?,
    val sourceLine: Int?,
    val sourceFunction: String?,
    val events: List<ParsedEvent>,
    val firstSeenIndex: Int,
    val children: MutableList<MutableSpanNode> = mutableListOf()
) {
    fun toSpanNode(): SpanNode = SpanNode(
        spanId = spanId,
        parentSpanId = parentSpanId,
        spanName = spanName,
        durationMs = durationMs,
        startTimestamp = startTimestamp,
        sourceComponent = sourceComponent,
        sourceOperation = sourceOperation,
        sourceLocationHint = sourceLocationHint,
        sourceFile = sourceFile,
        sourceLine = sourceLine,
        sourceFunction = sourceFunction,
        events = events,
        children = children.map { it.toSpanNode() }
    )
}

private val spanOrderComparator = compareBy<MutableSpanNode>(
    { it.startTimestamp == null },
    { it.startTimestamp },
    { it.firstSeenIndex },
    { it.spanId }
)

private fun sortChildren(node: MutableSpanNode) {
    node.children.sortWith(spanOrderComparator)
    node.children.forEach { sortChildren(it) }
}

private fun parseLogfmt(input: String): Map<String, String> {
    val result = linkedMapOf<String, String>()
    var index = 0
    val length = input.length

    fun skipSpaces() {
        while (index < length && input[index].isWhitespace()) index++
    }

    while (index < length) {
        skipSpaces()
        if (index >= length) break

        val keyStart = index
        while (index < length && input[index] != '=' && !input[index].isWhitespace()) {
            index++
        }
        if (index >= length || input[index] != '=') {
            // no '=' means malformed; skip the token
            while (index < length && !input[index].isWhitespace()) index++
            continue
        }
        val key = input.substring(keyStart, index)
        index++ // skip '='
        if (index >= length) {
            result[key] = ""
            break
        }

        val value = if (input[index] == '"') {
            index++
            val sb = StringBuilder()
            while (index < length) {
                val ch = input[index]
                if (ch == '\\' && index + 1 < length) {
                    sb.append(input[index + 1])
                    index += 2
                    continue
                }
                if (ch == '"') {
                    index++
                    break
                }
                sb.append(ch)
                index++
            }
            sb.toString()
        } else {
            val valueStart = index
            while (index < length && !input[index].isWhitespace()) index++
            input.substring(valueStart, index)
        }

        result[key] = value
    }

    return result
}

// Strip common logcat prefixes (epoch, threadtime, brief/tag) that can appear on stack trace lines
// when adb splits multi-line messages. Leave lines intact if they don't match a known header.
private fun cleanStackTrace(raw: String): String {
    return raw.split('\n').joinToString("\n") { stripLogcatPrefix(it.trimEnd()) }
}

private val logcatPrefixMatchers = listOf(
    // -v epoch: 1765068321.293 26055 26055 E Downloader:
    Regex("^\\s*\\d{10}\\.\\d{3}\\s+\\d+\\s+\\d+\\s+[VDIWEF]\\s+[^:]+:\\s*"),
    // -v threadtime / brief: 12-06 19:39:38.893 25580 25580 E Downloader:
    Regex("^\\s*\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+\\s+\\d+\\s+\\d+\\s+[VDIWEF]/?\\s*[^:]*:\\s*"),
    // -v tag / brief without PIDs: E/Downloader:
    Regex("^\\s*[VDIWEF]/[^:]+:\\s*")
)

private fun stripLogcatPrefix(line: String): String {
    for (regex in logcatPrefixMatchers) {
        val match = regex.find(line)
        if (match != null && match.range.first == 0) {
            val rest = line.substring(match.value.length)
            return normalizeStackLine(rest)
        }
    }
    // Fallback: strip everything up to and including the first colon-space if present.
    val simple = line.indexOf(": ")
    if (simple >= 0) return normalizeStackLine(line.substring(simple + 2))
    return line
}

private fun normalizeStackLine(rest: String): String =
    if (rest.startsWith("at ")) "    $rest" else rest

private fun normalizeFields(fields: MutableMap<String, String>): MutableMap<String, String> {
    val src = fields["src"]
    val hasSrcComp = fields.containsKey("src_comp")
    val hasSrcOp = fields.containsKey("src_op")
    if (src != null && !hasSrcComp && !hasSrcOp) {
        val parts = src.split('/', limit = 2)
        if (parts.size == 2) {
            fields["src_comp"] = parts[0]
            fields["src_op"] = parts[1]
        } else {
            fields["src_comp"] = src
        }
        if (!fields.containsKey("src_hint")) {
            fields["src_hint"] = src
        }
    }
    if (src != null && !fields.containsKey("src_hint")) {
        fields["src_hint"] = src
    }

    // Clean stack trace if present (after aliases so both names work).
    fields["stack_trace"]?.let { raw ->
        fields["stack_trace"] = cleanStackTrace(raw)
    }

    return fields
}

private data class HumanInfo(val logger: String?, val message: String?)

private val logcatThreadTimeRegex =
    // 12-08 15:12:55.610 10801 10801 D Downloader: message
    Regex("""^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+\d+\s+\d+\s+[VDIWEF]/?\s*(\S+):\s*(.*)$""")

private val logcatBriefRegex =
    // D/Downloader: message
    Regex("""^[VDIWEF]/\s*(\S+):\s*(.*)$""")

private fun parseHumanPrefix(human: String): HumanInfo {
    if (human.isBlank()) return HumanInfo(null, null)
    var text = human.trim()
    val glyphs = listOf("▫️", "🔍", "ℹ️", "⚠️", "❌", "💥")
    glyphs.forEach { if (text.startsWith(it)) text = text.removePrefix(it).trimStart() }

    // Logcat threadtime/brief lines (Android)
    logcatThreadTimeRegex.matchEntire(text)?.let { m ->
        val logger = m.groupValues[1].takeIf { it.isNotEmpty() }
        val msg = m.groupValues[2].takeIf { it.isNotEmpty() }
        return HumanInfo(logger, msg)
    }
    logcatBriefRegex.matchEntire(text)?.let { m ->
        val logger = m.groupValues[1].takeIf { it.isNotEmpty() }
        val msg = m.groupValues[2].takeIf { it.isNotEmpty() }
        return HumanInfo(logger, msg)
    }

    // Span start/end markers: "+++ name" or "----- name"
    if (text.startsWith("+++ ") || text.startsWith("--- ")) {
        val msg = text
        return HumanInfo(null, msg)
    }

    val colon = text.indexOf(':')
    if (colon > 0) {
        val logger = text.substring(0, colon).trim().takeIf { it.isNotEmpty() }
        val msg = text.substring(colon + 1).trim().takeIf { it.isNotEmpty() }
        return HumanInfo(logger, msg)
    }

    return HumanInfo(null, null)
}

private fun resolveMessage(logger: String?, head: String?, humanPrefix: String, human: HumanInfo): String? {
    val trimmedHuman = humanPrefix.trim()
    if (!head.isNullOrBlank()) {
        if (!logger.isNullOrBlank()) {
            val anchor = "$logger: $head"
            val idxAnchor = trimmedHuman.indexOf(anchor)
            if (idxAnchor >= 0) {
                val idxHead = trimmedHuman.indexOf(head, idxAnchor)
                if (idxHead >= 0) {
                    val full = trimmedHuman.substring(idxHead).trim()
                    if (full.isNotEmpty()) return full
                }
            }
        }
        val idxHead = trimmedHuman.indexOf(head)
        if (idxHead >= 0) {
            val full = trimmedHuman.substring(idxHead).trim()
            if (full.isNotEmpty()) return full
        }
        // Head is authoritative; prefer it over human prefix when not found in prefix.
        if (head.isNotBlank()) return head
    }
    val humanMessage = human.message?.takeIf { it.isNotBlank() }
    if (humanMessage != null) return humanMessage
    return if (trimmedHuman.isNotEmpty()) trimmedHuman else null
}
