package dev.goquick.kmpertrace.cli

import com.github.ajalt.clikt.core.parse
import dev.goquick.kmpertrace.cli.ansi.AnsiMode
import dev.goquick.kmpertrace.parse.buildTraces
import dev.goquick.kmpertrace.parse.parseLines
import java.nio.file.Files
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TreeCommandTest {

    private fun collectingProcessor(collected: MutableList<String>): PrintProcessor =
        { lines, _, _, _, _, _, _ -> collected.addAll(lines.toList()) }

    @Test
    fun file_option_reads_lines() {
        val tmp = Files.createTempFile("kmpertrace-cli-test", ".log")
        Files.write(tmp, listOf("one", "two"))

        val collected = mutableListOf<String>()
        PrintCommand(collectingProcessor(collected))
            .parse(listOf("--file", tmp.toString(), "--hide-source"))

        assertEquals(listOf("one", "two"), collected)
    }

    @Test
    fun custom_input_sequence_can_be_processed() {
        val input = sequenceOf("a", "b", "c")
        val collected = mutableListOf<String>()

        val cmd = object : PrintCommand(collectingProcessor(collected)) {
            override fun run() {
                processor(input, false, null, AnsiMode.OFF, TimeFormat.FULL, RawLogLevel.OFF, SpanAttrsMode.OFF)
            }
        }

        cmd.parse(emptyList())
        assertEquals(listOf("a", "b", "c"), collected)
    }

    @Test
    fun tree_command_builds_traces_with_parse_module() {
        val lines = sequenceOf(
            "t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_START name=\"root\" dur=0 head=\"start\" }|",
            "t DEBUG Api |{ ts=2025-01-01T00:00:01Z lvl=debug log=Api trace=trace-1 span=child parent=root kind=SPAN_END name=\"child\" dur=10 head=\"child end\" }|",
            "t INFO Api |{ ts=2025-01-01T00:00:02Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_END name=\"root\" dur=20 head=\"root end\" }|"
        )

        val records = parseLines(lines)
        val traces = buildTraces(records)
        assertEquals(1, traces.size)
        assertEquals("trace-1", traces.single().traceId)
        assertEquals(1, traces.single().spans.size)
        assertEquals(1, traces.single().spans.single().children.size)
    }

    @Test
    fun render_traces_produces_ascii_tree() {
        val lines = sequenceOf(
            "t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_START name=\"root\" dur=0 head=\"start\" }|",
            "t DEBUG Api |{ ts=2025-01-01T00:00:01Z lvl=debug log=Api trace=trace-1 span=child parent=root kind=SPAN_START name=\"child\" dur=0 head=\"child start\" }|",
            "t DEBUG Repo |{ ts=2025-01-01T00:00:02Z lvl=debug log=Repo trace=trace-1 span=child parent=root kind=LOG name=\"child\" dur=0 head=\"working\" }|",
            "t DEBUG Api |{ ts=2025-01-01T00:00:03Z lvl=debug log=Api trace=trace-1 span=child parent=root kind=SPAN_END name=\"child\" dur=15 head=\"child end\" }|",
            "t INFO Api |{ ts=2025-01-01T00:00:04Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_END name=\"root\" dur=30 head=\"root end\" }|"
        )

        val records = parseLines(lines)
        val traces = buildTraces(records)
        val rendered = renderTraces(traces, showSource = true, colorize = false, timeFormat = TimeFormat.FULL)

        val expected = """
            trace trace-1
            └─ root (30 ms)
               └─ child (15 ms)
                  └─ 🔍 2025-01-01T00:00:02Z Repo: working
        """.trimIndent()

        assertEquals(expected, rendered)
    }

    @Test
    fun render_traces_with_source_hints() {
        val lines = sequenceOf(
            "t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_START name=\"root\" dur=0 head=\"start\" src_comp=Repo src_op=rootOp src_hint=\"Repo.rootOp\" }|",
            "t DEBUG Repo |{ ts=2025-01-01T00:00:02Z lvl=debug log=Repo trace=trace-1 span=root parent=- kind=LOG name=\"root\" dur=0 head=\"working\" src_comp=Repo src_op=rootOp file=Repo.kt line=42 fn=loadProfile }|"
        )

        val records = parseLines(lines)
        val traces = buildTraces(records)
        val rendered = renderTraces(traces, showSource = true, colorize = false, timeFormat = TimeFormat.FULL)

        val expected = """
            trace trace-1
            └─ root (0 ms) [Repo.rootOp]
               └─ 🔍 2025-01-01T00:00:02Z Repo: working (Repo.kt:42 loadProfile)
        """.trimIndent()

        assertEquals(expected, rendered)
    }

    @Test
    fun render_traces_wraps_and_renders_stack_trace() {
        val lines = sequenceOf(
            "t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_START name=\"root\" dur=0 head=\"start\" }|",
            "t ERROR Api |{ ts=2025-01-01T00:00:01Z lvl=error log=Api trace=trace-1 span=root parent=- kind=LOG name=\"root\" dur=0 head=\"boom\" stack_trace=\"java.lang.IllegalStateException: boom\\n    at A.foo(A.kt:10)\\n    at B.bar(B.kt:20)\" }|",
            "t INFO Api |{ ts=2025-01-01T00:00:02Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_END name=\"root\" dur=20 head=\"end\" }|"
        )

        val records = parseLines(lines)
        val traces = buildTraces(records)
        val rendered = renderTraces(traces, showSource = false, maxLineWidth = 60, colorize = false, timeFormat = TimeFormat.FULL)

        assertTrue(rendered.contains("trace trace-1"))
        assertTrue(rendered.contains("└─ root (20 ms)"))
        assertTrue(rendered.contains("❌")) // error marker present
        assertTrue(rendered.contains("A.foo"))
        assertTrue(rendered.contains("B.bar"))
    }

    @Test
    fun render_traces_marks_error_status_even_when_level_is_info() {
        val lines = sequenceOf(
            "t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_START name=\"root\" dur=0 head=\"start\" }|",
            "t INFO Api |{ ts=2025-01-01T00:00:01Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_END name=\"root\" dur=20 head=\"end\" status=\"ERROR\" stack_trace=\"java.lang.IllegalStateException: boom\" }|"
        )

        val rendered = renderTraces(buildTraces(parseLines(lines)), colorize = false, timeFormat = TimeFormat.FULL)

        assertTrue(rendered.contains("❌ 2025-01-01T00:00:01Z Api: end"))
    }

    @Test
    fun render_traces_with_time_only_format() {
        val lines = sequenceOf(
            "t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_START name=\"root\" dur=0 head=\"start\" }|",
            "t DEBUG Repo |{ ts=2025-01-01T00:00:02Z lvl=debug log=Repo trace=trace-1 span=root parent=- kind=LOG name=\"root\" dur=0 head=\"working\" }|"
        )

        val records = parseLines(lines)
        val traces = buildTraces(records)
        val rendered =
            renderTraces(traces, showSource = false, colorize = false, timeFormat = TimeFormat.TIME_ONLY, zoneId = ZoneOffset.UTC)

        assertTrue(rendered.contains("00:00:02.000 Repo: working"))
        assertTrue(!rendered.contains("2025-01-01T00:00:02Z Repo: working"))
    }

    @Test
    fun render_untraced_records_are_listed() {
        val lines = sequenceOf(
            "t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_START name=\"root\" dur=0 head=\"start\" }|",
            "t DEBUG Loose |{ ts=2025-01-01T00:00:01Z lvl=debug log=Loose trace=0 span=0 parent=0 kind=LOG name=\"-\" dur=0 head=\"untraced\" }|"
        )

        val records = parseLines(lines)
        val traces = buildTraces(records)
        val untraced = records.filter { it.traceId == "0" }
        val rendered =
            renderTraces(
                traces,
                untracedRecords = untraced,
                showSource = false,
                colorize = false,
                timeFormat = TimeFormat.TIME_ONLY,
                zoneId = ZoneOffset.UTC
            )

        assertTrue(rendered.contains("trace trace-1"))
        assertTrue(rendered.contains("00:00:01.000 Loose: untraced"))
        // untraced log should appear after the trace block in timeline order
        val idxLoose = rendered.indexOf("Loose: untraced")
        val idxTrace = rendered.indexOf("trace trace-1")
        assertTrue(idxTrace in 0 until idxLoose)
    }

    @Test
    fun raw_timeline_is_sorted_by_time_not_string() {
        val structured = sequenceOf(
            "t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_START name=\"root\" dur=0 head=\"start\" }|",
            "t INFO Api |{ ts=2025-01-01T00:00:00.500Z lvl=info log=Api trace=trace-1 span=root parent=- kind=LOG name=\"root\" dur=0 head=\"in\" }|",
            "t INFO Api |{ ts=2025-01-01T00:00:01.000Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_END name=\"root\" dur=1000 head=\"end\" }|"
        )
        val records = parseLines(structured)
        val traces = buildTraces(records)

        val raw = rawRecordFromLine(
            "2025-01-01 00:00:02.000  5510-5510  ziparchive              dev.goquick.kmpertrace.sampleapp     W  after",
            RawLogLevel.ALL
        )
        val rendered = renderTraces(
            traces,
            untracedRecords = listOfNotNull(raw),
            showSource = false,
            colorize = false,
            timeFormat = TimeFormat.TIME_ONLY,
            zoneId = ZoneOffset.UTC
        )

        val idxTrace = rendered.indexOf("trace trace-1")
        val idxAfter = rendered.indexOf("ziparchive")
        assertTrue(idxTrace >= 0 && idxAfter >= 0, "expected both trace and raw record:\n$rendered")
        assertTrue(idxTrace < idxAfter, "expected raw record to render after trace:\n$rendered")
    }

    @Test
    fun render_multiline_messages_as_multiple_lines() {
        val lines = sequenceOf(
            "t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_START name=\"root\" dur=0 head=\"start\" }|",
            "t ERROR Api |{ ts=2025-01-01T00:00:01Z lvl=error log=Api trace=trace-1 span=root parent=- kind=LOG name=\"root\" dur=0 head=\"line1\\nline2\" }|",
            "t INFO Api |{ ts=2025-01-01T00:00:02Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_END name=\"root\" dur=10 head=\"end\" }|"
        )

        val rendered =
            renderTraces(buildTraces(parseLines(lines)), colorize = false, timeFormat = TimeFormat.TIME_ONLY, zoneId = ZoneOffset.UTC)

        assertTrue(rendered.contains("00:00:01.000 Api: line1"))
        assertTrue(rendered.contains("line2"))
    }
}
