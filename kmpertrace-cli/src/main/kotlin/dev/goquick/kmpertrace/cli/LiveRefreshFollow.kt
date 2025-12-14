package dev.goquick.kmpertrace.cli

import com.github.ajalt.mordant.rendering.Theme
import com.github.ajalt.mordant.terminal.Terminal
import dev.goquick.kmpertrace.analysis.AnalysisEngine
import dev.goquick.kmpertrace.analysis.FilterState
import dev.goquick.kmpertrace.cli.ansi.AnsiMode
import dev.goquick.kmpertrace.cli.ansi.shouldColorize
import java.io.BufferedReader
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal sealed interface FollowEvent {
    data class Line(val line: String) : FollowEvent
    data object Eof : FollowEvent
}

internal fun processFollowLiveRefresh(
    reader: BufferedReader,
    showSource: Boolean,
    maxLineWidth: Int?,
    ansiMode: AnsiMode,
    timeFormat: TimeFormat,
    rawLogsLevel: RawLogLevel,
    spanAttrsMode: SpanAttrsMode,
    autoWidth: Boolean,
    isStdin: Boolean
) {
    val colorize = ansiMode.shouldColorize()
    val terminal = Terminal(theme = Theme.Default)
    val running = AtomicBoolean(true)

    val controller = TuiController(
        engine = AnalysisEngine(filterState = FilterState(), maxRecords = 5_000),
        filters = FilterState(),
        maxRecords = 5_000,
        promptSearch = { null }
    ).apply { setInitialModes(rawLogsLevel = rawLogsLevel, spanAttrsMode = spanAttrsMode) }

    val queue = LinkedBlockingQueue<FollowEvent>(10_000)
    val outputIsTty = System.console() != null
    val enableScreenRefresh = outputIsTty
    if (enableScreenRefresh) {
        Runtime.getRuntime().addShutdownHook(Thread { showCursor() })
        hideCursor()
    }

    fun clearForRefresh() {
        // Clear screen but keep scrollback (unlike the TUI).
        print("\u001B[H\u001B[2J")
    }

    // Producer: stdin blocks; file uses ready()/sleep so it can tail appended lines.
    kotlin.concurrent.thread(isDaemon = true, name = "kmpertrace-print-follow-reader") {
        try {
            reader.use { r ->
                if (isStdin) {
                    while (running.get()) {
                        val line = r.readLine() ?: break
                        queue.put(FollowEvent.Line(line))
                    }
                    queue.offer(FollowEvent.Eof)
                } else {
                    while (running.get()) {
                        if (!r.ready()) {
                            Thread.sleep(50)
                            continue
                        }
                        val line = r.readLine()
                        if (line != null) queue.put(FollowEvent.Line(line))
                    }
                }
            }
        } catch (_: Exception) {
            queue.offer(FollowEvent.Eof)
        }
    }

    var lastRendered: String? = null
    var lastWidth: Int? = null
    val refreshNanos = 300_000_000L
    var lastRenderAt = 0L

    fun maybeRender(force: Boolean) {
        val now = System.nanoTime()
        if (!force && now - lastRenderAt < refreshNanos) return
        lastRenderAt = now

        val wrapWidth = if (autoWidth) {
            val w = terminal.updateSize().width.takeIf { it > 0 }
            w ?: detectConsoleWidth()
        } else maxLineWidth
        if (autoWidth && wrapWidth != null && wrapWidth != lastWidth) {
            lastWidth = wrapWidth
        }

        val renderData = controller.buildRenderData()
        val rendered = renderTraces(
            traces = renderData.snapshot.traces,
            untracedRecords = renderData.snapshot.untraced + renderData.rawList,
            showSource = showSource,
            maxLineWidth = wrapWidth,
            colorize = colorize,
            timeFormat = timeFormat,
            spanAttrsMode = controller.state.spanAttrsMode
        ).ifBlank { "No structured KmperTrace log records found yet..." }

        val rawDirty = controller.isRawDirty()
        if (!force && rendered == lastRendered && !rawDirty) return
        lastRendered = rendered
        if (enableScreenRefresh) clearForRefresh()
        println(rendered)
        System.out.flush()
        controller.clearRawDirty()
    }

    // Initial render so the terminal isn't blank.
    maybeRender(force = true)

    while (running.get()) {
        val evt = queue.poll(250, TimeUnit.MILLISECONDS)
        when (evt) {
            null -> {
                if (autoWidth) {
                    val w = terminal.updateSize().width.takeIf { it > 0 }
                    val resolved = w ?: detectConsoleWidth()
                    if (resolved != lastWidth) {
                        lastWidth = resolved
                        maybeRender(force = true)
                        continue
                    }
                }
                if (controller.isRawDirty()) {
                    maybeRender(force = false)
                }
            }
            is FollowEvent.Line -> {
                val update = controller.handleLine(evt.line)
                if (update.forceRender) {
                    maybeRender(force = true)
                } else {
                    maybeRender(force = false)
                }
            }
            FollowEvent.Eof -> running.set(false)
        }
    }

    controller.flush()
    maybeRender(force = true)
    if (enableScreenRefresh) showCursor()
}

private fun hideCursor() {
    print("\u001B[?25l")
    System.out.flush()
}

private fun showCursor() {
    print("\u001B[?25h")
    System.out.flush()
}
