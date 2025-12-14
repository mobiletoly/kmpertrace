package dev.goquick.kmpertrace.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import dev.goquick.kmpertrace.analysis.AnalysisEngine
import dev.goquick.kmpertrace.analysis.AnalysisSnapshot
import dev.goquick.kmpertrace.analysis.FilterState
import dev.goquick.kmpertrace.cli.ansi.AnsiMode
import dev.goquick.kmpertrace.cli.ansi.shouldColorize
import dev.goquick.kmpertrace.cli.source.Sources
import dev.goquick.kmpertrace.parse.ParsedLogRecord
import java.io.BufferedReader
import java.nio.file.Path

/**
 * Signature for functions that consume parsed lines and render output.
 */
typealias PrintProcessor = (Sequence<String>, Boolean, Int?, AnsiMode, TimeFormat, RawLogLevel, SpanAttrsMode) -> Unit

/**
 * Entry point for the KmperTrace CLI.
 */
fun main(args: Array<String>) = KmperTraceCli()
    .subcommands(
        PrintCommand(),
        TuiCommand()
    )
    .main(args)

/**
 * Top-level CLI command wiring subcommands and version info.
 */
class KmperTraceCli : CliktCommand(name = "kmpertrace-cli") {
    init {
        versionOption(BuildInfo.VERSION)
    }

    override fun run() = Unit
}

/**
 * Command that reads structured KmperTrace logs and renders them once (non-interactive).
 */
open class PrintCommand(
    internal val processor: PrintProcessor = ::processLines
) : CliktCommand(name = "print") {

    /**
     * Short description for `clikt` help output.
     */
    override fun help(context: Context): String =
        "Render traces from structured KmperTrace logs (non-interactive)"

    private val file: Path? by option(
        "--file",
        "-f",
        help = "Path to log file; reads stdin when omitted"
    )
        .path(mustExist = true, canBeFile = true, canBeDir = false, mustBeReadable = true)
    private val hideSource: Boolean by option(
        "-H",
        "--hide-source",
        help = "Hide source component/operation metadata"
    ).flag(default = false)
    private val maxLineWidthOpt: String? by option(
        "-w",
        "--max-line-width",
        help = "Wrap output lines at this width; use auto for terminal width, unlimited/0 for no wrap"
    ).validate { validateMaxWidth(it) }
    private val color: String? by option(
        "-C",
        "--color",
        help = "Color output: auto|on|off (default: auto)"
    )
    private val timeFormatOpt: String? by option(
        "-T",
        "--time-format",
        help = "Timestamp display: full|time-only (default: time-only)"
    )
    private val follow: Boolean by option(
        "--follow",
        "-F",
        help = "Follow streaming input (stdin or tail) and live-refresh"
    ).flag(default = false)
    private val rawLogs: String? by option(
        "--raw-logs",
        help = "Include raw (non-KmperTrace) lines: off|all|verbose|debug|info|warn|error|assert (default: off)"
    ).validate { validateRawLevel(it) }
    private val spanAttrs: String? by option(
        "--span-attrs",
        help = "Show span attributes: off|on (default: off)"
    ).validate { validateSpanAttrsMode(it) }

    override fun run() {
        val ansiMode = parseAnsiMode(color)
        val timeFormat = parseTimeFormat(timeFormatOpt)
        val (resolvedWidth, autoWidth) = resolveWidth(maxLineWidthOpt, autoByDefault = false)
        val rawLevel = parseRawLevel(rawLogs)
        val spanAttrsMode = parseSpanAttrsMode(spanAttrs)
        if (follow) {
            val reader: BufferedReader = if (file != null) {
                file!!.toFile().bufferedReader()
            } else {
                System.`in`.bufferedReader()
            }
            processFollow(
                reader,
                !hideSource,
                resolvedWidth,
                ansiMode,
                timeFormat,
                rawLevel,
                spanAttrsMode,
                statusLabel = file?.toString() ?: "stdin",
                autoWidth = autoWidth
            )
        } else {
            if (file != null) {
                file!!.toFile().bufferedReader().useLines { lines ->
                    processor(lines, !hideSource, resolvedWidth, ansiMode, timeFormat, rawLevel, spanAttrsMode)
                }
            } else {
                processor(
                    generateSequence(::readLine),
                    !hideSource,
                    resolvedWidth,
                    ansiMode,
                    timeFormat,
                    rawLevel,
                    spanAttrsMode
                )
            }
        }
    }
}

/**
 * Interactive TUI command that streams logs from a chosen source and live-refreshes the tree view.
 */
class TuiCommand : CliktCommand(name = "tui") {
    override fun help(context: Context): String =
        "Interactive TUI for live KmperTrace logs (adb/ios/file/stdin)"

    private val sourceOpt: String? by option(
        "-s",
        "--source",
        help = "Log source: file|adb|ios|stdin (default: stdin)"
    )
    private val file: Path? by option("--file", "-f", help = "Path to log file when --source=file")
        .path(mustExist = true, canBeFile = true, canBeDir = false, mustBeReadable = true)
    private val adbCmd: String? by option(
        "--adb-cmd",
        help = "Command to stream Android logs (default builds from --adb-pkg)"
    )
    private val adbPkg: String? by option(
        "--adb-pkg",
        help = "Android package; builds default adb command when set"
    )
    private val iosCmd: String? by option(
        "--ios-cmd",
        help = "Command to stream iOS logs (default builds from --ios-proc)"
    )
    private val iosProc: String? by option(
        "--ios-proc",
        help = "iOS process name; builds default simctl command when set"
    )
    private val hideSource: Boolean by option(
        "-H",
        "--hide-source",
        help = "Hide source component/operation metadata"
    ).flag(default = false)
    private val maxLineWidthOpt: String? by option(
        "-w",
        "--max-line-width",
        help = "Wrap output lines at this width; use auto for terminal width, unlimited/0 for no wrap"
    ).validate { validateMaxWidth(it) }
    private val color: String? by option(
        "-C",
        "--color",
        help = "Color output: auto|on|off (default: auto)"
    )
    private val timeFormatOpt: String? by option(
        "-T",
        "--time-format",
        help = "Timestamp display: full|time-only (default: time-only)"
    )
    private val minLevelOpt: String? by option(
        "-m",
        "--min-level",
        help = "Minimum level: verbose|debug|info|warn|error|assert"
    )
    private val traceFilter: String? by option(
        "--trace-id",
        help = "Only include records for this trace id"
    )
    private val componentFilter: String? by option(
        "--component",
        help = "Only include records with this source component"
    )
    private val operationFilter: String? by option(
        "--operation",
        help = "Only include records with this source operation"
    )
    private val textFilter: String? by option(
        "-F",
        "--filter",
        help = "Substring filter applied to message/stack"
    )
    private val excludeUntraced: Boolean by option(
        "--exclude-untraced",
        help = "Drop records with trace=0"
    ).flag(default = false)
    private val maxRecordsOpt: Int? by option(
        "-M",
        "--max-records",
        help = "Buffer size before dropping oldest records (default 5000)"
    ).int()
    private val rawLogs: String? by option(
        "--raw-logs",
        help = "Include raw (non-KmperTrace) lines: off|all|verbose|debug|info|warn|error|assert (default: off)"
    ).validate { validateRawLevel(it) }
    private val spanAttrs: String? by option(
        "--span-attrs",
        help = "Show span attributes: off|on (default: off)"
    ).validate { validateSpanAttrsMode(it) }

    override fun run() {
        val ansiMode = parseAnsiMode(color)
        val timeFormat = parseTimeFormat(timeFormatOpt)
        val filters = FilterState(
            minLevel = minLevelOpt?.lowercase(),
            traceId = traceFilter,
            component = componentFilter,
            operation = operationFilter,
            text = textFilter,
            excludeUntraced = excludeUntraced
        )
        val (resolvedWidth, autoWidth) = resolveWidth(maxLineWidthOpt, autoByDefault = true)
        val rawLevel = parseRawLevel(rawLogs)
        val spanAttrsMode = parseSpanAttrsMode(spanAttrs)

        val resolvedSource =
            Sources.resolve(sourceOpt, file != null, adbCmd, adbPkg, iosCmd, iosProc)
        val reader = Sources.readerFor(resolvedSource, file, adbCmd, adbPkg, iosCmd, iosProc)

        TuiRunner(
            reader = reader,
            showSource = !hideSource,
            maxLineWidth = resolvedWidth,
            ansiMode = ansiMode,
            timeFormat = timeFormat,
            statusLabel = resolvedSource,
            filters = filters,
            maxRecords = maxRecordsOpt ?: 5_000,
            autoWidth = autoWidth,
            rawLogsLevel = rawLevel,
            spanAttrsMode = spanAttrsMode
        ).run()
    }
}

private fun processLines(
    lines: Sequence<String>,
    showSource: Boolean,
    maxLineWidth: Int?,
    ansiMode: AnsiMode,
    timeFormat: TimeFormat,
    rawLevel: RawLogLevel,
    spanAttrsMode: SpanAttrsMode
) {
    val ingested = ingestLines(lines, FilterState(), rawLevel, maxRecords = 10_000)
    if (ingested.snapshot.traces.isEmpty() && ingested.snapshot.untraced.isEmpty() && ingested.raw.isEmpty()) {
        println("No structured KmperTrace log records found.")
        return
    }

    val colorize = ansiMode.shouldColorize()
    val rendered = renderTraces(
        traces = ingested.snapshot.traces,
        untracedRecords = ingested.snapshot.untraced + ingested.raw,
        showSource = showSource,
        maxLineWidth = maxLineWidth,
        colorize = colorize,
        timeFormat = timeFormat,
        spanAttrsMode = spanAttrsMode
    )
    println(rendered)
}

private fun processFollow(
    reader: BufferedReader,
    showSource: Boolean,
    maxLineWidth: Int?,
    ansiMode: AnsiMode,
    timeFormat: TimeFormat,
    rawLevel: RawLogLevel,
    spanAttrsMode: SpanAttrsMode,
    statusLabel: String?,
    autoWidth: Boolean
) {
    val isStdin = statusLabel == "stdin"
    processFollowLiveRefresh(
        reader = reader,
        showSource = showSource,
        maxLineWidth = maxLineWidth,
        ansiMode = ansiMode,
        timeFormat = timeFormat,
        rawLogsLevel = rawLevel,
        spanAttrsMode = spanAttrsMode,
        autoWidth = autoWidth,
        isStdin = isStdin
    )
}

/**
 * Controls whether timestamps are shown with full ISO strings or time-only.
 */
enum class TimeFormat { FULL, TIME_ONLY }

private data class IngestionResult(val snapshot: AnalysisSnapshot, val raw: List<ParsedLogRecord>)

private fun ingestLines(
    lines: Sequence<String>,
    filters: FilterState,
    rawLevel: RawLogLevel,
    maxRecords: Int
): IngestionResult {
    val engine = AnalysisEngine(filterState = filters, maxRecords = maxRecords)
    val raw = mutableListOf<ParsedLogRecord>()
    lines.forEach { line ->
        val update = engine.ingest(line)
        if (rawLevel != RawLogLevel.OFF) {
            update.rawCandidates.forEach { candidate ->
                rawRecordFromLine(candidate, rawLevel)?.let { raw += it }
            }
        }
    }
    val flushUpdate = engine.flush()
    if (rawLevel != RawLogLevel.OFF) {
        flushUpdate.rawCandidates.forEach { candidate ->
            rawRecordFromLine(candidate, rawLevel)?.let { raw += it }
        }
    }
    return IngestionResult(engine.snapshot(), if (rawLevel == RawLogLevel.OFF) emptyList() else raw)
}
