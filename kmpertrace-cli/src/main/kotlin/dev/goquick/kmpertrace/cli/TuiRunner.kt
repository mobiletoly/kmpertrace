package dev.goquick.kmpertrace.cli

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.rendering.Theme
import com.github.ajalt.mordant.terminal.Terminal
import dev.goquick.kmpertrace.analysis.AnalysisEngine
import dev.goquick.kmpertrace.analysis.AnalysisSnapshot
import dev.goquick.kmpertrace.analysis.FilterState
import dev.goquick.kmpertrace.cli.ansi.AnsiMode
import dev.goquick.kmpertrace.cli.ansi.shouldColorize
import dev.goquick.kmpertrace.cli.ansi.stripAnsi
import java.io.BufferedReader
import kotlin.system.exitProcess

internal class TuiRunner(
    private val reader: BufferedReader,
    private val showSource: Boolean,
    private val maxLineWidth: Int?,
    private val ansiMode: AnsiMode,
    private val timeFormat: TimeFormat,
    private val statusLabel: String? = null,
    private val filters: FilterState = FilterState(),
    private val maxEvents: Int = 5_000,
    private val autoWidth: Boolean = false,
    private val rawLogsLevel: RawLogLevel = RawLogLevel.OFF
) {
    fun run() {
        val colorize = ansiMode.shouldColorize()
        val terminal = Terminal(theme = Theme.Default)
        val engine = AnalysisEngine(filterState = filters, maxEvents = maxEvents)
        val pendingLines = StringBuilder()
        val refreshNanos = 300_000_000L // 300ms between redraws
        var lastRender = System.nanoTime()
        val clearRequested = java.util.concurrent.atomic.AtomicBoolean(false)
        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val inputRawMode = java.util.concurrent.atomic.AtomicBoolean(false)
        val helpRequested = java.util.concurrent.atomic.AtomicBoolean(false)
        val reRenderRequested = java.util.concurrent.atomic.AtomicBoolean(true)
        val searchPromptRequested = java.util.concurrent.atomic.AtomicBoolean(false)
        val searchTerm = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val promptInProgress = java.util.concurrent.atomic.AtomicBoolean(false)
        val minLevelState = java.util.concurrent.atomic.AtomicReference(filters.minLevel)
        val showRaw = java.util.concurrent.atomic.AtomicBoolean(rawLogsLevel != RawLogLevel.OFF)
        val rawLevelState = java.util.concurrent.atomic.AtomicReference(rawLogsLevel)
        val rawLines = ArrayDeque<dev.goquick.kmpertrace.parse.ParsedEvent>()
        var lastWidth: Int? = if (autoWidth) terminal.updateSize().width.takeIf { it > 0 } else maxLineWidth
        val androidGrouper = AndroidMultilineGrouper()

        fun startKeyListener() {
            inputRawMode.set(enableRawMode())
            if (inputRawMode.get()) {
                Runtime.getRuntime().addShutdownHook(Thread { disableRawMode() })
            }
            kotlin.concurrent.thread(isDaemon = true, name = "kmpertrace-tui-keys") {
                if (inputRawMode.get()) {
                    val input = System.`in`
                    while (running.get()) {
                        if (promptInProgress.get()) {
                            Thread.sleep(50)
                            continue
                        }
                        val ch = input.read()
                        if (ch == -1) break
                        val key = ch.toChar()
                        if (helpRequested.get() && key != '?') {
                            helpRequested.set(false)
                            reRenderRequested.set(true)
                        }
                        when (key) {
                            'c', 'C' -> {
                                clearRequested.set(true)
                                reRenderRequested.set(true)
                            }
                            'r', 'R' -> {
                                val next = nextRawLevel(showRaw.get(), rawLevelState.get())
                                showRaw.set(next != RawLogLevel.OFF)
                                rawLevelState.set(next)
                                reRenderRequested.set(true)
                            }
                            'l', 'L' -> {
                                val next = nextStructuredLevel(minLevelState.get())
                                minLevelState.set(next)
                                engine.updateFilter(filters.copy(minLevel = next))
                                reRenderRequested.set(true)
                            }
                            '/' -> {
                                promptInProgress.set(true)
                                searchPromptRequested.set(true)
                                reRenderRequested.set(true)
                            }
                            '?' -> {
                                helpRequested.set(!helpRequested.get())
                                reRenderRequested.set(true)
                            }
                            'q', 'Q' -> {
                                running.set(false)
                                disableRawMode()
                                exitProcess(0)
                            }
                        }
                    }
                } else {
                    val br = System.`in`.bufferedReader()
                    while (running.get()) {
                        if (promptInProgress.get()) {
                            Thread.sleep(50)
                            continue
                        }
                        val line = br.readLine() ?: break
                        if (helpRequested.get()) {
                            helpRequested.set(false)
                            reRenderRequested.set(true)
                        }
                        when (line.trim().lowercase()) {
                            "c", "clear", ":clear", "/clear" -> {
                                clearRequested.set(true)
                                reRenderRequested.set(true)
                            }
                            "r", "raw" -> {
                                val next = nextRawLevel(showRaw.get(), rawLevelState.get())
                                showRaw.set(next != RawLogLevel.OFF)
                                rawLevelState.set(next)
                                reRenderRequested.set(true)
                            }
                            "l", "level" -> {
                                val next = nextStructuredLevel(minLevelState.get())
                                minLevelState.set(next)
                                engine.updateFilter(filters.copy(minLevel = next))
                                reRenderRequested.set(true)
                            }
                            "/", ":search", "search" -> {
                                searchPromptRequested.set(true)
                                reRenderRequested.set(true)
                            }
                            "?", "help", ":help" -> {
                                helpRequested.set(!helpRequested.get())
                                reRenderRequested.set(true)
                            }
                            "q", "quit", ":q" -> {
                                running.set(false)
                                exitProcess(0)
                            }
                            else -> {
                                // ignore unknown commands to keep output clean
                            }
                        }
                    }
                }
            }
        }

        val allowInput = statusLabel != "stdin"
        if (allowInput) startKeyListener()

        fun renderIfDue(force: Boolean = false) {
            val now = System.nanoTime()
            if (!force && now - lastRender < refreshNanos) return
            lastRender = now
            if (helpRequested.get()) {
                clearScreenAndScrollback()
                println(renderHelp(colorize))
                System.out.flush()
                return
            }
            val wrapWidth = if (autoWidth) {
                val w = terminal.updateSize().width.takeIf { it > 0 }
                val fallback = detectConsoleWidth()
                val resolved = w ?: lastWidth ?: fallback
                lastWidth = resolved
                resolved
            } else maxLineWidth
            val snapshot = applySearchFilter(engine.snapshot(), searchTerm.get())
        val rawList = if (showRaw.get()) {
            val term = searchTerm.get()
            val predicate = filters.predicate()
            rawLines.filter { evt ->
                rawLevelAllows(evt, rawLevelState.get()) &&
                        predicate(evt) &&
                        (term.isNullOrBlank() || matchesEvent(evt, term.trim()))
            }
        } else emptyList()
            val rendered = renderTraces(snapshot.traces, snapshot.untraced + rawList, showSource, wrapWidth, colorize, timeFormat)
            clearScreenAndScrollback()
            if (rendered.isBlank()) {
                terminal.println("No structured KmperTrace events found yet... (Ctrl+C to exit)")
            } else {
                terminal.println(rendered)
            }
            terminal.println()
            val status = buildStatusLine(
                statusLabel,
                snapshot,
                filters,
                colorize,
                maxEvents,
                searchTerm.get(),
                terminal,
                minLevelState.get(),
                showRaw.get(),
                rawLevelState.get()
            )
            if (status.isNotEmpty()) {
                terminal.println(status)
                if (allowInput && !helpRequested.get()) {
                    terminal.print(": ")
                }
            }
            System.out.flush()
        }

        renderIfDue(force = true)

        fun handleLine(line: String) {
            val openStructured = hasOpenStructured(pendingLines)
            // Fast-path: if this single line looks complete and parses as structured, send to engine directly.
            if (!openStructured && line.contains("|{") && line.trimEnd().endsWith("}|")) {
                engine.onLine(line)
                renderIfDue(force = true)
                return
            }
            // Otherwise, only collect raw when it does not parse as structured (avoids KmperTrace leakage).
            if (!openStructured && !line.contains("|{") && dev.goquick.kmpertrace.parse.parseLine(line) == null) {
                rawEventFromLine(line, RawLogLevel.ALL)?.let { evt ->
                    rawLines += evt
                    while (rawLines.size > maxEvents) rawLines.removeFirst()
                }
            }
            if (pendingLines.isNotEmpty()) pendingLines.append('\n')
            pendingLines.append(line)

            val buffered = pendingLines.toString()
            val lastOpen = buffered.lastIndexOf("|{")
            val lastClose = buffered.lastIndexOf("}|")
            if (lastOpen != -1 && lastClose != -1 && lastClose > lastOpen) {
                // Preserve the human prefix by passing the full buffered chunk up to the close marker.
                val candidate = buffered.substring(0, lastClose + 2)
                pendingLines.clear()
                engine.onLine(candidate)
                renderIfDue(force = true)
            } else if (pendingLines.length > 50_000) {
                pendingLines.clear()
            }
        }

        reader.use { r ->
            while (running.get()) {
                if (reRenderRequested.getAndSet(false)) {
                    renderIfDue(force = true)
                }
                if (clearRequested.getAndSet(false)) {
                    engine.reset()
                    pendingLines.clear()
                    renderIfDue(force = true)
                    continue
                }
                if (searchPromptRequested.getAndSet(false) && allowInput) {
                    helpRequested.set(false)
                    promptInProgress.set(true)
                    val term = promptForSearch(inputRawMode)
                    promptInProgress.set(false)
                    searchTerm.set(term)
                    reRenderRequested.set(true)
                    renderIfDue(force = true)
                    continue
                }
                if (!r.ready()) {
                    if (autoWidth) {
                        val w = terminal.updateSize().width.takeIf { it > 0 }
                        if (w != null && w != lastWidth) {
                            lastWidth = w
                            renderIfDue(force = true)
                        }
                    }
                    Thread.sleep(50)
                    continue
                }
                val line = r.readLine() ?: break
                androidGrouper.feed(line).forEach { collapsed ->
                    handleLine(collapsed)
                }
            }
        }

        androidGrouper.flush().forEach { handleLine(it) }

        renderIfDue(force = true)
        val finalSnapshot = engine.snapshot()
        if (finalSnapshot.traces.isEmpty() && finalSnapshot.untraced.isEmpty()) {
            println("No structured KmperTrace events found.")
        }
        System.out.flush()
        exitProcess(0)
    }
}

private const val ANSI_CLEAR_SCREEN_AND_SCROLLBACK = "\u001B[H\u001B[2J\u001B[3J"

private fun clearScreenAndScrollback() {
    print(ANSI_CLEAR_SCREEN_AND_SCROLLBACK)
}

internal fun nextRawLevel(currentEnabled: Boolean, currentLevel: RawLogLevel): RawLogLevel =
    when {
        !currentEnabled -> RawLogLevel.ALL // turn on from OFF
        currentLevel == RawLogLevel.OFF -> RawLogLevel.ALL
        currentLevel == RawLogLevel.ALL -> RawLogLevel.DEBUG
        currentLevel == RawLogLevel.DEBUG -> RawLogLevel.INFO
        currentLevel == RawLogLevel.INFO -> RawLogLevel.WARN
        currentLevel == RawLogLevel.WARN -> RawLogLevel.ERROR
        currentLevel == RawLogLevel.ERROR -> RawLogLevel.OFF
        currentLevel == RawLogLevel.VERBOSE -> RawLogLevel.DEBUG // not used directly
        currentLevel == RawLogLevel.ASSERT -> RawLogLevel.ERROR  // not used directly
        else -> RawLogLevel.DEBUG
    }

private fun nextStructuredLevel(current: String?): String? = when (current) {
    null, "all" -> "debug"
    "debug" -> "info"
    "info" -> "error"
    "error" -> "all"
    else -> "debug"
}

private fun buildStatusLine(
    label: String?,
    snapshot: AnalysisSnapshot,
    filters: FilterState,
    colorize: Boolean,
    maxEvents: Int,
    search: String?,
    terminal: Terminal,
    minLevel: String?,
    rawEnabled: Boolean,
    rawLevel: RawLogLevel
): String {
    val traces = snapshot.traces.size
    val errors = errorCount(snapshot)
    val parts = mutableListOf<String>()
    label?.let { parts += "src=$it" }
    parts += "traces=$traces"
    parts += "errors=$errors"
    parts += "[l] logs=${minLevel ?: "all"}"
    parts += "[r] raw-logs=${if (rawEnabled) rawLevel.name.lowercase() else "off"}"
    if (snapshot.droppedCount > 0) parts += "dropped=${snapshot.droppedCount}/${maxEvents}"
    val filterSummary = filters.describe()
    if (filterSummary.isNotBlank()) parts += "filters=$filterSummary"
    search?.takeIf { it.isNotBlank() }?.let { parts += "filter=\"$it\"" }
    // raw toggle info is handled separately in runner (printed after render)
    parts += "[?] help"
    parts += "[/] filter"
    parts += "[c] clear"
    parts += "[q] quit"
    if (parts.isEmpty()) return ""

    val plain = parts.joinToString(" | ")
    val targetWidth = terminal.updateSize().width.takeIf { it > 0 } ?: stripAnsi(plain).length
    if (!colorize) return plain.padEnd(targetWidth)

    val bg = TextColors.white.bg
    val fg = TextColors.black
    val traceColor = TextColors.blue
    val errColor = if (errors > 0) TextColors.red else fg
    val activeErrorTag = TextColors.white + TextColors.red.bg
    val keyStyle = TextStyles.bold + fg
    val content = parts.joinToString(" | ") { part ->
        when {
            part.startsWith("traces=") -> "traces=" + traceColor(part.removePrefix("traces="))
            part.startsWith("errors=") -> "errors=" + errColor(part.removePrefix("errors="))
            part.startsWith("[l] logs=") -> keyStyle("[l]") + fg(" logs=" + part.removePrefix("[l] logs="))
            part.startsWith("[r] raw-logs=") -> keyStyle("[r]") + fg(" raw-logs=" + part.removePrefix("[r] raw-logs="))
            part == "[?] help" -> keyStyle("[?]") + fg(" help")
            part == "[/] filter" -> keyStyle("[/]") + fg(" filter")
            part == "[c] clear" -> keyStyle("[c]") + fg(" clear")
            part == "[q] quit" -> keyStyle("[q]") + fg(" quit")
            else -> part
        }
    }
    val visibleLen = stripAnsi(content).length
    val padded = if (visibleLen < targetWidth) content + " ".repeat(targetWidth - visibleLen) else content
    return bg(fg(padded))
}

private fun promptForSearch(rawEnabled: java.util.concurrent.atomic.AtomicBoolean): String? {
    val wasRaw = rawEnabled.get()
    if (wasRaw) {
        disableRawMode()
        rawEnabled.set(false)
    }
    print("enter filter term (empty to clear): ")
    System.out.flush()
    val line = readLine()
    println()
    if (wasRaw) {
        rawEnabled.set(enableRawMode())
    }
    val term = line?.trim().orEmpty()
    return term.ifBlank { null }
}

private fun detectConsoleWidth(): Int {
    System.getenv("COLUMNS")?.toIntOrNull()?.let { if (it > 0) return it }
    return 80
}

private fun enableRawMode(): Boolean {
    val os = System.getProperty("os.name").lowercase()
    if (!(os.contains("mac") || os.contains("linux") || os.contains("nix"))) return false
    return try {
        ProcessBuilder("sh", "-c", "stty -icanon -echo </dev/tty").start().waitFor() == 0
    } catch (_: Exception) {
        false
    }
}

private fun disableRawMode() {
    val os = System.getProperty("os.name").lowercase()
    if (!(os.contains("mac") || os.contains("linux") || os.contains("nix"))) return
    try {
        ProcessBuilder("sh", "-c", "stty sane </dev/tty").start().waitFor()
    } catch (_: Exception) {
        // ignore
    }
}

private fun hasOpenStructured(buffer: StringBuilder): Boolean {
    val open = buffer.lastIndexOf("|{")
    val close = buffer.lastIndexOf("}|")
    return open != -1 && open > close
}

internal fun rawLevelAllows(evt: dev.goquick.kmpertrace.parse.ParsedEvent, min: RawLogLevel): Boolean {
    if (min == RawLogLevel.OFF) return false
    val lvlStr = evt.rawFields["lvl"]?.uppercase() ?: "ALL"
    val actual = runCatching { RawLogLevel.valueOf(lvlStr) }.getOrDefault(RawLogLevel.ALL)
    if (min == RawLogLevel.ALL) return true
    if (actual == RawLogLevel.ALL) return true // pass through unknown/unspecified raw levels
    return actual.ordinal >= min.ordinal
}
