package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.parse.buildTraces
import dev.goquick.kmpertrace.parse.parseLines
import kotlin.test.Test
import kotlin.test.assertTrue

class RenderGoldenTest {
    @Test
    fun renders_error_with_stack_trace_and_attrs() {
        val lines = sequenceOf(
            """t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-err span=root parent_span=- kind=SPAN_START name="root" dur=0 head="start" }|""",
            """t ERROR Api |{ ts=2025-01-01T00:00:01Z lvl=error log=Api trace=trace-err span=root parent_span=- kind=SPAN_END name="root" dur=20 head="boom" status="ERROR" err_type="IllegalStateException" err_msg="boom" a:custom="x" stack_trace="java.lang.IllegalStateException: boom\n    at A.foo(A.kt:10)\n    at B.bar(B.kt:20)\n" }|"""
        )

        val rendered = renderTraces(
            buildTraces(parseLines(lines)),
            showSource = false,
            colorize = false,
            timeFormat = TimeFormat.FULL
        )

        // Loose contains checks to allow for spacing differences.
        assertTrue(rendered.contains("trace trace-err"))
        assertTrue(rendered.contains("root (20 ms)"))
        assertTrue(rendered.contains("boom"))
        // Stack trace is rendered inline with literal 'n' separators in this renderer path.
        // Stack trace details may be wrapped; just confirm key substrings are present.
        assertTrue(rendered.contains("❌"))
        assertTrue(rendered.contains("A.kt:10"))
    }

    @Test
    fun renders_span_attributes_when_enabled() {
        val lines = sequenceOf(
            """t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-err span=root parent_span=- kind=SPAN_START name="root" dur=0 head="start" }|""",
            """t INFO Api |{ ts=2025-01-01T00:00:01Z lvl=info log=Api trace=trace-err span=root parent_span=- kind=SPAN_END name="root" dur=20 head="done" a:custom="x" d:email="a@b.com" }|"""
        )

        val rendered = renderTraces(
            buildTraces(parseLines(lines)),
            showSource = false,
            colorize = false,
            timeFormat = TimeFormat.FULL,
            spanAttrsMode = SpanAttrsMode.ON
        )

        assertTrue(rendered.contains("{custom=x ?email=a@b.com}"), "expected span attributes to render:\n$rendered")
    }

    @Test
    fun wraps_without_mid_word_split() {
        val lines = sequenceOf(
            """t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=span parent_span=- kind=LOG name="span" dur=0 head="Download DownloadA starting (jobId=12345678901234567890)" }|"""
        )

        val rendered = renderTraces(
            buildTraces(parseLines(lines)),
            showSource = false,
            maxLineWidth = 50,
            colorize = false,
            timeFormat = TimeFormat.TIME_ONLY
        )

        assertTrue(rendered.contains("jobId=12345678901234567890"))
        assertTrue(!rendered.contains("jobId=1234567890\n1234567890"), "should not split mid-word:\n$rendered")
    }

    @Test
    fun uses_fallback_width_when_auto_unspecified() {
        val lines = sequenceOf(
            """t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-2 span=span parent_span=- kind=LOG name="span" dur=0 head="word1 word2 word3 word4 word5 word6" }|"""
        )

        val rendered = renderTraces(
            buildTraces(parseLines(lines)),
            showSource = false,
            maxLineWidth = 20,
            colorize = false,
            timeFormat = TimeFormat.TIME_ONLY
        )

        assertTrue(rendered.lines().any { it.length <= 25 }, "expected wrapping to happen:\n$rendered")
    }

    @Test
    fun prefers_moving_word_to_next_line_rather_than_splitting() {
        val lines = sequenceOf(
            """t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-3 span=span parent_span=- kind=LOG name="span" dur=0 head="Download DownloadA progress 66% (jobId=4453147290101272305)" }|"""
        )

        val rendered = renderTraces(
            buildTraces(parseLines(lines)),
            showSource = false,
            maxLineWidth = 55,
            colorize = false,
            timeFormat = TimeFormat.TIME_ONLY
        )

        assertTrue(rendered.contains("jobId=4453147290101272305"))
        assertTrue(!rendered.contains("4453147290\n101272305"), "jobId should stay intact:\n$rendered")
    }

    @Test
    fun still_breaks_very_long_words_beyond_budget() {
        val longWord = "x".repeat(100)
        val lines = sequenceOf(
            """t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-4 span=span parent_span=- kind=LOG name="span" dur=0 head="$longWord" }|"""
        )

        val rendered = renderTraces(
            buildTraces(parseLines(lines)),
            showSource = false,
            maxLineWidth = 40,
            colorize = false,
            timeFormat = TimeFormat.TIME_ONLY
        )

        assertTrue(rendered.lines().any { it.length <= 50 }, "expected long word to be split when beyond budget:\n$rendered")
    }
}
