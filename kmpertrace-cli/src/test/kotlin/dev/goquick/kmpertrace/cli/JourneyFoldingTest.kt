package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.cli.ansi.AnsiPalette
import dev.goquick.kmpertrace.parse.buildTraces
import dev.goquick.kmpertrace.parse.parseLines
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertTrue

class JourneyFoldingTest {

    @Test
    fun journey_span_kind_renders_as_journey_header() {
        val lines = sequenceOf(
            "t INFO Api |{ ts=2025-01-01T00:00:00.000Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_START name=\"SignInHost.submit\" dur=0 head=\"start\" span_kind=journey a:trigger=\"tap.SignIn.Submit\" }|",
            "t INFO Api |{ ts=2025-01-01T00:00:02.000Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_END name=\"SignInHost.submit\" dur=2000 head=\"end\" a:trigger=\"tap.SignIn.Submit\" }|"
        )

        val records = parseLines(lines)
        val traces = buildTraces(records)
        val rendered = renderTraces(traces, colorize = false, timeFormat = TimeFormat.TIME_ONLY, zoneId = ZoneOffset.UTC)

        assertTrue(
            "journey at 00:00:00.000 (trigger=tap.SignIn.Submit)" in rendered,
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
    }

    @Test
    fun journey_header_colors_at_like_timestamp() {
        val lines = sequenceOf(
            "t INFO Api |{ ts=2025-01-01T00:00:00.000Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_START name=\"SignInHost.submit\" dur=0 head=\"start\" span_kind=journey a:trigger=\"tap.SignIn.Submit\" }|",
            "t INFO Api |{ ts=2025-01-01T00:00:02.000Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_END name=\"SignInHost.submit\" dur=2000 head=\"end\" }|"
        )

        val records = parseLines(lines)
        val traces = buildTraces(records)
        val rendered = renderTraces(traces, colorize = true, timeFormat = TimeFormat.TIME_ONLY, zoneId = ZoneOffset.UTC)

        val expected = "${AnsiPalette.timestamp}at${AnsiPalette.reset} ${AnsiPalette.timestamp}00:00:00.000${AnsiPalette.reset}"
        assertTrue(expected in rendered, "expected `at` to be colored like timestamp:\n$rendered")
    }
}
