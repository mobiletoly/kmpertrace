package dev.goquick.kmpertrace.parse

/**
 * Parse a single log entry that contains a structured KmperTrace suffix (`|{ ... }|`).
 *
 * Notes:
 * - `kind` is optional; when missing, the log record kind defaults to [LogRecordKind.LOG].
 * - `trace`/`span` are optional; when missing, they default to `"0"` (untraced).
 *
 * Returns null when the input does not contain a valid structured suffix or is missing required
 * base fields (currently `ts` and `lvl`).
 */
fun parseLine(line: String): ParsedLogRecord? {
    val trimmed = line.trimEnd()
    val start = trimmed.lastIndexOf("|{")
    val end = trimmed.lastIndexOf("}|")
    if (start == -1 || end == -1 || end <= start + 2) return null
    val humanPrefix = stripLogcatPrefixesFromContinuationLines(trimmed.substring(0, start).trimEnd())

    val structured = trimmed.substring(start + 2, end).trim()
    if (structured.isEmpty()) return null

    val fields = normalizeFields(parseLogfmt(structured).toMutableMap())
    if (!(fields.containsKey("ts") && fields.containsKey("lvl"))) {
        return null
    }

    val traceId = fields["trace"] ?: "0"
    val kindStr = fields["kind"]
    val logRecordKind = if (kindStr == null) {
        LogRecordKind.LOG
    } else {
        runCatching { LogRecordKind.valueOf(kindStr) }.getOrNull() ?: return null
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

    return ParsedLogRecord(
        traceId = traceId,
        spanId = spanId,
        parentSpanId = parentSpanId,
        logRecordKind = logRecordKind,
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
 * Parse multiple entries; drops lines that cannot be parsed.
 *
 * The input is assumed to arrive as lines (e.g. `readLine()` / file iteration), but structured
 * entries may span multiple lines due to embedded stack traces. This function frames entries by
 * buffering until it sees a closing `}|` that matches an earlier `|{`.
 */
fun parseLines(lines: Sequence<String>): List<ParsedLogRecord> =
    frameStructuredSuffixEntries(lines).mapNotNull(::parseLine).toList()

/**
 * Parse multiple lines provided via [Iterable]; drops lines that cannot be parsed.
 */
fun parseLines(lines: Iterable<String>): List<ParsedLogRecord> =
    parseLines(lines.asSequence())
