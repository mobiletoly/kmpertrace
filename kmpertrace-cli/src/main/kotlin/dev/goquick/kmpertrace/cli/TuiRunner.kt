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
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class TuiRunner(
    private val reader: BufferedReader,
    private val showSource: Boolean,
    private val maxLineWidth: Int?,
    private val ansiMode: AnsiMode,
    private val timeFormat: TimeFormat,
    private val statusLabel: String? = null,
    private val filters: FilterState = FilterState(),
    private val maxRecords: Int = 5_000,
    private val autoWidth: Boolean = false,
    private val rawLogsLevel: RawLogLevel = RawLogLevel.OFF,
    private val spanAttrsMode: SpanAttrsMode = SpanAttrsMode.OFF
) {
    private sealed interface LoopEvent {
        data class Ui(val event: UiEvent) : LoopEvent
        data class Line(val line: String) : LoopEvent
        data object Eof : LoopEvent
    }

    fun run() {
        val colorize = ansiMode.shouldColorize()
        val terminal = Terminal(theme = Theme.Default)
        val engine = AnalysisEngine(filterState = filters, maxRecords = maxRecords)
        val refreshNanos = 300_000_000L // 300ms between redraws
        var lastRender = System.nanoTime()
        val queue = LinkedBlockingDeque<LoopEvent>(10_000)
        val inputRawModeState = AtomicBoolean(false)
        val running = AtomicBoolean(true)
        val promptInProgress = AtomicBoolean(false)

        val controller = TuiController(
            engine = engine,
            filters = filters,
            maxRecords = maxRecords,
            promptSearch = {
                promptInProgress.set(true)
                try {
                    promptForSearch(inputRawModeState)
                } finally {
                    promptInProgress.set(false)
                }
            }
        ).apply {
            setInitialModes(rawLogsLevel = rawLogsLevel, spanAttrsMode = spanAttrsMode)
        }

        var lastWidth: Int? = if (autoWidth) terminal.updateSize().width.takeIf { it > 0 } else maxLineWidth

        fun requestExit() {
            if (!running.compareAndSet(true, false)) return
            try {
                reader.close()
            } catch (_: Exception) {
                // ignore
            }
            // If the queue is full of log lines, ensure we can wake the main loop immediately.
            queue.clear()
            queue.offerFirst(LoopEvent.Eof)
        }

        fun startKeyListener() {
            val inputRawMode = enableRawMode()
            inputRawModeState.set(inputRawMode)
            if (inputRawMode) Runtime.getRuntime().addShutdownHook(Thread { disableRawMode() })
            fun post(evt: UiEvent) {
                queue.offerFirst(LoopEvent.Ui(evt))
            }
            kotlin.concurrent.thread(isDaemon = true, name = "kmpertrace-tui-keys") {
                if (inputRawMode) {
                    val input = System.`in`
                    while (running.get()) {
                        if (promptInProgress.get()) {
                            Thread.sleep(50)
                            continue
                        }
                        val ch = input.read()
                        if (ch == -1) break
                        val c = ch.toChar()
                        if (c == 'q' || c == 'Q') {
                            requestExit()
                            break
                        }
                        if (c == '/') {
                            // Avoid consuming prompt input typed immediately after '/'.
                            promptInProgress.set(true)
                        }
                        uiEventsForKey(c).forEach { post(it) }
                    }
                } else {
                    val br = System.`in`.bufferedReader()
                    while (running.get()) {
                        if (promptInProgress.get()) {
                            Thread.sleep(50)
                            continue
                        }
                        val line = br.readLine() ?: break
                        uiEventsForCommand(line).forEach { post(it) }
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
            val state = controller.state
            if (state.helpVisible) {
                clearScreenAndScrollback()
                println(renderHelp(colorize))
                controller.rawDirtyAndClear()
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
            val renderData = controller.buildRenderData()
            val snapshot = renderData.snapshot
            val rawList = renderData.rawList
            val rendered = renderTraces(
                traces = snapshot.traces,
                untracedRecords = snapshot.untraced + rawList,
                showSource = showSource,
                maxLineWidth = wrapWidth,
                colorize = colorize,
                timeFormat = timeFormat,
                spanAttrsMode = state.spanAttrsMode
            )
            clearScreenAndScrollback()
            if (rendered.isBlank()) {
                terminal.println("No structured KmperTrace log records found yet...")
            } else {
                terminal.println(rendered)
            }
            terminal.println()
            val status = buildStatusLine(
                snapshot = snapshot,
                colorize = colorize,
                maxRecords = maxRecords,
                search = state.search,
                terminal = terminal,
                minLevel = state.minLevel,
                rawEnabled = state.rawEnabled,
                rawLevel = state.rawLevel,
                spanAttrsMode = state.spanAttrsMode
            )
            if (status.isNotEmpty()) {
                terminal.println(status)
                if (allowInput && !state.helpVisible) {
                    terminal.print(": ")
                }
            }
            controller.rawDirtyAndClear()
            System.out.flush()
        }

        renderIfDue(force = true)

        kotlin.concurrent.thread(isDaemon = true, name = "kmpertrace-tui-reader") {
            try {
                reader.use { r ->
                    while (running.get()) {
                        val line = r.readLine() ?: break
                        queue.putLast(LoopEvent.Line(line))
                    }
                }
            } finally {
                queue.offerLast(LoopEvent.Eof)
            }
        }

        while (running.get()) {
            val evt = if (autoWidth) {
                queue.poll(250, TimeUnit.MILLISECONDS)
            } else {
                queue.takeFirst()
            }

            var needsForceRender = false

            if (evt == null) {
                if (autoWidth) {
                    val w = terminal.updateSize().width.takeIf { it > 0 }
                    if (w != null && w != lastWidth) {
                        lastWidth = w
                        needsForceRender = true
                    }
                }
            } else {
                fun process(one: LoopEvent) {
                    when (one) {
                        is LoopEvent.Ui -> {
                            val update = controller.handleUiEvent(one.event, allowInput = allowInput)
                            needsForceRender = needsForceRender || update.forceRender
                            if (update.exitRequested) {
                                requestExit()
                            }
                        }

                        is LoopEvent.Line -> {
                            val update = controller.handleLine(one.line)
                            needsForceRender = needsForceRender || update.forceRender
                        }

                        LoopEvent.Eof -> running.set(false)
                    }
                }

                process(evt)
                // Drain any immediately available events to coalesce state changes and reduce re-renders.
                while (true) {
                    val next = queue.poll() ?: break
                    process(next)
                    if (!running.get()) break
                }
            }

            if (!running.get()) break

            if (needsForceRender) {
                renderIfDue(force = true)
                continue
            }
            // Raw updates are throttled by renderIfDue's refresh interval.
            if (controller.rawDirtyAndClear()) {
                renderIfDue()
            }
        }

        val finalUpdate = controller.flush()
        if (finalUpdate.forceRender) renderIfDue(force = true)

        renderIfDue(force = true)
        val finalSnapshot = controller.snapshot()
        if (finalSnapshot.traces.isEmpty() && finalSnapshot.untraced.isEmpty()) {
            println("No structured KmperTrace log records found.")
        }
        System.out.flush()
        disableRawMode()
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

private fun buildStatusLine(
    snapshot: AnalysisSnapshot,
    colorize: Boolean,
    maxRecords: Int,
    search: String?,
    terminal: Terminal,
    minLevel: String?,
    rawEnabled: Boolean,
    rawLevel: RawLogLevel,
    spanAttrsMode: SpanAttrsMode
): String {
    val errors = errorCount(snapshot)
    val parts = mutableListOf<String>()
    parts += "errors=$errors"
    parts += "[s] struct-logs=${displayMinLevel(minLevel)}"
    parts += "[r] raw-logs=${if (rawEnabled) rawLevel.name.lowercase() else "off"}"
    parts += "[a] attrs=${spanAttrsMode.name.lowercase()}"
    if (snapshot.droppedCount > 0) parts += "dropped=${snapshot.droppedCount}/${maxRecords}"
    val filterValue = search?.takeIf { it.isNotBlank() }
    parts += "[/] filter=${filterValue?.let { quoteForStatus(it) } ?: "off"}"
    // raw toggle info is handled separately in runner (printed after render)
    parts += "[?] help"
    parts += "[c] clear"
    parts += "[q] quit"
    if (parts.isEmpty()) return ""

    val plain = parts.joinToString(" | ")
    val targetWidth = terminal.updateSize().width.takeIf { it > 0 } ?: stripAnsi(plain).length
    if (!colorize) return plain.padEnd(targetWidth)

    val bg = TextColors.white.bg
    val fg = TextColors.black
    val errColor = if (errors > 0) TextColors.red else fg
    val keyStyle = TextStyles.bold + fg
    val content = parts.joinToString(" | ") { part ->
        when {
            part.startsWith("errors=") -> "errors=" + errColor(part.removePrefix("errors="))
            part.startsWith("[a] attrs=") -> keyStyle("[a]") + fg(" attrs=" + part.removePrefix("[a] attrs="))
            part.startsWith("[r] raw-logs=") -> keyStyle("[r]") + fg(" raw-logs=" + part.removePrefix("[r] raw-logs="))
            part.startsWith("[s] struct-logs=") -> keyStyle("[s]") + fg(" struct-logs=" + part.removePrefix("[s] struct-logs="))
            part.startsWith("[/] filter=") -> keyStyle("[/]") + fg(" filter=" + part.removePrefix("[/] filter="))
            part == "[?] help" -> keyStyle("[?]") + fg(" help")
            part == "[c] clear" -> keyStyle("[c]") + fg(" clear")
            part == "[q] quit" -> keyStyle("[q]") + fg(" quit")
            else -> part
        }
    }
    val visibleLen = stripAnsi(content).length
    val padded = if (visibleLen < targetWidth) content + " ".repeat(targetWidth - visibleLen) else content
    return bg(fg(padded))
}

private fun displayMinLevel(value: String?): String {
    val normalized = value?.lowercase()
    return when (normalized) {
        null, "all" -> "all"
        "verbose", "debug", "info", "warn", "error", "assert" -> normalized
        else -> "all"
    }
}

private fun quoteForStatus(value: String): String =
    buildString {
        append('"')
        value.forEach { ch ->
            if (ch == '"') append("\\\"") else append(ch)
        }
        append('"')
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

internal fun rawLevelAllows(evt: dev.goquick.kmpertrace.parse.ParsedLogRecord, min: RawLogLevel): Boolean {
    if (min == RawLogLevel.OFF) return false
    val lvlStr = evt.rawFields["lvl"]?.uppercase() ?: "ALL"
    val actual = runCatching { RawLogLevel.valueOf(lvlStr) }.getOrDefault(RawLogLevel.ALL)
    if (min == RawLogLevel.ALL) return true
    if (actual == RawLogLevel.ALL) return true // pass through unknown/unspecified raw levels
    return actual.ordinal >= min.ordinal
}
