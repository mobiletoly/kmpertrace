package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.cli.ansi.AnsiPalette
import dev.goquick.kmpertrace.parse.buildTraces
import dev.goquick.kmpertrace.parse.parseLines
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertTrue

class JourneyFoldingTest {

    @Test
    fun journey_started_is_folded_into_root_span_header_and_not_rendered_as_log_line() {
        val lines = sequenceOf(
            "t INFO Api |{ ts=2025-01-01T00:00:00.000Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_START name=\"SignInHost.submit\" dur=0 head=\"start\" }|",
            "t INFO Api |{ ts=2025-01-01T00:00:01.000Z lvl=info log=SignInHost trace=trace-1 span=root parent=- kind=LOG name=\"-\" dur=0 head=\"journey started (trigger=tap.SignIn.Submit)\" }|",
            "t INFO Api |{ ts=2025-01-01T00:00:02.000Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_END name=\"SignInHost.submit\" dur=2000 head=\"end\" a:trigger=\"tap.SignIn.Submit\" }|"
        )

        val records = parseLines(lines)
        val traces = buildTraces(records)
        val rendered = renderTraces(traces, colorize = false, timeFormat = TimeFormat.TIME_ONLY, zoneId = ZoneOffset.UTC)

        assertTrue(
            "journey at 00:00:01.000 (trigger=tap.SignIn.Submit)" in rendered,
            "expected folded journey header:\n$rendered"
        )
        assertTrue(
            "(2000 ms)" !in rendered,
            "expected journey header to omit duration:\n$rendered"
        )
        assertTrue(
            "{trigger=tap.SignIn.Submit}" !in rendered,
            "expected trigger attribute to be suppressed for journey header:\n$rendered"
        )
        assertTrue(
            "SignInHost: journey started" !in rendered,
            "expected journey started log line to be omitted:\n$rendered"
        )
    }

    @Test
    fun journey_header_colors_at_like_timestamp() {
        val lines = sequenceOf(
            "t INFO Api |{ ts=2025-01-01T00:00:00.000Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_START name=\"SignInHost.submit\" dur=0 head=\"start\" }|",
            "t INFO Api |{ ts=2025-01-01T00:00:01.000Z lvl=info log=SignInHost trace=trace-1 span=root parent=- kind=LOG name=\"-\" dur=0 head=\"journey started (trigger=tap.SignIn.Submit)\" }|",
            "t INFO Api |{ ts=2025-01-01T00:00:02.000Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_END name=\"SignInHost.submit\" dur=2000 head=\"end\" }|"
        )

        val records = parseLines(lines)
        val traces = buildTraces(records)
        val rendered = renderTraces(traces, colorize = true, timeFormat = TimeFormat.TIME_ONLY, zoneId = ZoneOffset.UTC)

        val expected = "${AnsiPalette.timestamp}at${AnsiPalette.reset} ${AnsiPalette.timestamp}00:00:01.000${AnsiPalette.reset}"
        assertTrue(expected in rendered, "expected `at` to be colored like timestamp:\n$rendered")
    }
}
