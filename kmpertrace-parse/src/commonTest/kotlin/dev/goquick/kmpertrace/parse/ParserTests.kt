package dev.goquick.kmpertrace.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParserTests {

    @Test
    fun parse_and_build_simple_trace() {
        val lines = listOf(
            "t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_START name=\"root\" dur=0 head=\"start\" src_comp=Repo src_op=load src_hint=\"Repo.load\" }|",
            "t DEBUG Api |{ ts=2025-01-01T00:00:01Z lvl=debug log=Api trace=trace-1 span=child parent=root kind=SPAN_START name=\"child\" dur=0 head=\"child start\" }|",
            "t DEBUG Repo |{ ts=2025-01-01T00:00:02Z lvl=debug log=Repo trace=trace-1 span=child parent=root kind=LOG name=\"child\" dur=0 head=\"working\" src_comp=Repo src_op=childOp }|",
            "t DEBUG Api |{ ts=2025-01-01T00:00:03Z lvl=debug log=Api trace=trace-1 span=child parent=root kind=SPAN_END name=\"child\" dur=15 head=\"child end\" }|",
            "t INFO Api |{ ts=2025-01-01T00:00:04Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_END name=\"root\" dur=30 head=\"root end\" }|"
        )

        val records = parseLines(lines)
        assertEquals(5, records.size)
        assertEquals("Repo", records.first().sourceComponent)
        assertEquals("load", records.first().sourceOperation)
        assertEquals("Repo.load", records.first().sourceLocationHint)

        val traces = buildTraces(records)
        assertEquals(1, traces.size)
        val trace = traces.single()
        assertEquals("trace-1", trace.traceId)
        assertEquals(1, trace.spans.size)

        val root = trace.spans.single()
        assertEquals("root", root.spanName)
        assertEquals(30, root.durationMs)
        assertEquals(1, root.children.size)
        assertTrue(root.records.isEmpty())

        val child = root.children.single()
        assertEquals("child", child.spanName)
        assertEquals(15, child.durationMs)
        assertEquals(1, child.records.size)
        val record = child.records.single()
        assertEquals("working", record.message)
        assertEquals("Repo", record.sourceComponent)
    }

    @Test
    fun prefers_human_message_over_truncated_head() {
        val line =
            "Downloader: Download DownloadA progress 33% (jobId=-56743) |{ ts=2025-12-08T22:21:59.752Z lvl=debug log=Downloader trace=abc span=span parent=parent head=\"Download Downlo\" }|"
        val record = parseLine(line)
        assertNotNull(record)
        assertEquals("Download DownloadA progress 33% (jobId=-56743)", record!!.message)
    }

    @Test
    fun anchor_uses_head_as_cursor_but_returns_full_message() {
        val line =
            "Net: Very long message text that exceeds head |{ ts=2025-12-08T22:21:59.000Z lvl=info log=Net trace=traceX span=spanX parent=parent head=\"Very long mess\" }|"
        val record = parseLine(line)
        assertNotNull(record)
        assertEquals("Very long message text that exceeds head", record!!.message)
    }

    @Test
    fun parses_android_threadtime_with_head_cursor() {
        val line =
            "12-08 17:56:03.806  5330  5330 D ProfileRepository: ProfileRepository: loadProfile finished for user-123 |{ ts=2025-12-08T22:56:03.806286Z lvl=debug trace=37cfecfeb2483dff span=e9075c50ddc8d39d parent=5772b12ba9b6aa89 head=\"loadProfile fin\" src=ProfileRepository/loadProfile log=ProfileRepository svc=sample-app thread=\"main\" }|"
        val record = parseLine(line)
        assertNotNull(record)
        assertEquals("loadProfile finished for user-123", record!!.message)
    }

    @Test
    fun parses_android_threadtime_chunk_message_with_truncated_head() {
        val line =
            "12-08 18:05:29.501  5330  5330 D Downloader: Downloader: Storing chunk 1/3 for DownloadA (65536 bytes, jobId=30493) |{ ts=2025-12-08T23:05:29.500817Z lvl=debug trace=6fced952a755760c span=cee13a9e76244bde parent=aaa747251383737b head=\"Storing chunk 1\" src=Downloader/storeChunk log=Downloader svc=sample-app thread=\"main\" }|"
        val record = parseLine(line)
        assertNotNull(record)
        assertEquals("Storing chunk 1/3 for DownloadA (65536 bytes, jobId=30493)", record!!.message)
    }

    @Test
    fun parses_multiline_stack_trace_in_structured_suffix() {
        val line = """
            [Error] 2025-12-09T03:28:00.876Z ERROR Downloader --- Downloader.DownloadA |{ ts=2025-12-09T03:28:00.876Z lvl=error trace=b93dc2e1178a8c11 span=6c268bf7dff86d57 parent=84e1c921ffb25177 kind=SPAN_END name="Downloader.DownloadA" dur=1156 head="--- Downloader." src=Downloader/DownloadA log=Downloader svc=sample-app a:jobId="90589" a:chunks="3" status="ERROR" err_type="IllegalStateException" err_msg="Fatal network error on \"DownloadA\" at 66% (jobId=90589)" throwable="IllegalStateException" stack_trace="
IllegalStateException: Fatal network error on \"DownloadA\" at 66% (jobId=90589)
    at First.line
    at Second.line
" }| ([wasm code], line 0)
        """.trimIndent()
        val record = parseLine(line)
        assertNotNull(record)
        val stack = record.rawFields["stack_trace"] ?: error("missing stack_trace")
        assertTrue(stack.contains("First.line"))
        assertTrue(stack.contains("Second.line"))
    }

    @Test
    fun non_traced_events_are_parsed_but_not_in_traces() {
        val line = "t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info kind=LOG head=\"plain\" log=Api }|"
        val record = parseLine(line)
        assertNotNull(record)
        assertEquals("0", record.traceId)

        val traces = buildTraces(listOf(record))
        assertTrue(traces.isEmpty())
    }

    @Test
    fun malformed_lines_return_null() {
        assertNull(parseLine("no pipe here"))
        assertNull(parseLine("prefix |{ ts=now kind=LOG }|"))
    }

    @Test
    fun partial_span_is_represented_best_effort() {
        val line =
            "t INFO Api |{ ts=2025-01-01T00:00:04Z lvl=info log=Api trace=trace-2 span=solo parent=- kind=SPAN_END name=\"solo\" dur=42 head=\"only end\" }|"
        val record = parseLine(line)
        assertNotNull(record)

        val traces = buildTraces(listOf(record))
        assertEquals(1, traces.size)
        val span = traces.single().spans.single()
        assertEquals("solo", span.spanName)
        assertEquals(42, span.durationMs)
    }

    @Test
    fun span_end_with_stack_trace_is_attached_as_log() {
        val lines = listOf(
            "t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_START name=\"root\" dur=0 head=\"start\" }|",
            "t INFO Api |{ ts=2025-01-01T00:00:01Z lvl=info log=Api trace=trace-1 span=root parent=- kind=SPAN_END name=\"root\" dur=10 head=\"end\" stack_trace=\"boom\" }|"
        )

        val traces = buildTraces(parseLines(lines))
        assertEquals(1, traces.size)
        val span = traces.single().spans.single()
        assertEquals(1, span.records.size)
        assertEquals("boom", span.records.single().rawFields["stack_trace"])
    }

    @Test
    fun parses_multiline_message_when_wrapped() {
        val lines = listOf(
            """
            prefix |{ ts=2025-01-01T00:00:01Z lvl=debug log=Loose trace=0 span=0 parent=0 kind=LOG name="-" dur=0 head="line1
            line2" }|
            """.trimIndent()
        )

        val records = parseLines(lines)
        val record = records.single()
        assertEquals("line1\nline2", record.message)
    }

    @Test
    fun ignores_browser_prefix_and_suffix() {
        val safariCopy =
            "[Log] 2025-12-06T21:44:47.790Z DEBUG ProfileViewModel refreshAll called |{ ts=2025-12-06T21:44:47.790Z lvl=debug log=ProfileViewModel trace=abc span=1 parent=0 kind=LOG name=\"-\" dur=0 head=\"refreshAll called\" }| ([wasm code], line 0)"
        val chromeCopy =
            "kmpertrace-sample-app.uninstantiated.mjs:219 2025-12-06T21:50:29.849Z DEBUG Database Saved 3 activity events |{ ts=2025-12-06T21:50:29.849Z lvl=debug log=Database trace=abc span=child parent=root kind=LOG name=\"Database.saveActivity\" dur=0 head=\"Saved 3 activity events\" }| extra-tail"

        val safariRecord = parseLine(safariCopy)
        val chromeRecord = parseLine(chromeCopy)

        assertNotNull(safariRecord)
        assertNotNull(chromeRecord)
        assertEquals("abc", safariRecord.traceId)
        assertEquals("abc", chromeRecord.traceId)
        assertEquals("ProfileViewModel", safariRecord.loggerName)
        assertEquals("Database", chromeRecord.loggerName)
    }

    @Test
    fun picks_last_structured_block_when_multiple_like_prefix_contains_brace() {
        val line =
            "weird }| prefix |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=root parent=- kind=LOG name=\"-\" dur=0 head=\"ok\" }| trailing"
        val record = parseLine(line)
        assertNotNull(record)
        assertEquals("trace-1", record.traceId)
        assertEquals("ok", record.message)
    }

    @Test
    fun rejects_lines_missing_core_fields_even_if_delimiters_present() {
        // no ts and lvl
        val line = "prefix |{ log=Api trace=abc span=1 kind=LOG head=\"missing ts\" }|"
        assertNull(parseLine(line))
    }

    @Test
    fun parses_when_message_contains_delimiter_tokens() {
        val line =
            """human prefix with pipes |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=span-1 parent=- kind=LOG name="root" dur=1 head="value with }| inside" }| trailing noise"""

        val record = parseLine(line)
        assertNotNull(record)
        assertEquals("value with }| inside", record.message)
        assertEquals("trace-1", record.traceId)
    }

    @Test
    fun spans_without_logs_are_sorted_by_start_timestamp() {
        val lines = listOf(
            """h |{ ts=2025-01-01T00:00:00Z lvl=info log=App trace=trace-1 span=root parent=- kind=SPAN_START name="root" }|""",
            """h |{ ts=2025-01-01T00:00:01Z lvl=info log=App trace=trace-1 span=childA parent=root kind=SPAN_START name="A" }|""",
            """h |{ ts=2025-01-01T00:00:02Z lvl=info log=App trace=trace-1 span=childB parent=root kind=SPAN_START name="B" }|""",
            """h |{ ts=2025-01-01T00:00:03Z lvl=info log=App trace=trace-1 span=childA parent=root kind=SPAN_END name="A" dur=10 }|""",
            """h |{ ts=2025-01-01T00:00:04Z lvl=info log=App trace=trace-1 span=childB parent=root kind=SPAN_END name="B" dur=10 }|"""
        )

        val traces = buildTraces(parseLines(lines))
        val root = traces.single().spans.single()
        assertEquals(listOf("A", "B"), root.children.map { it.spanName })
    }

    @Test
    fun ignores_lines_where_structured_suffix_missing() {
        val line = "just text with }| but no structured suffix"
        assertNull(parseLine(line))
    }

    @Test
    fun uses_last_delimiter_when_multiple_pipes_present() {
        val line =
            "noise |{ not real }| real |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=span-1 parent=- kind=LOG head=\"ok\" }| trailer"
        val record = parseLine(line)
        assertNotNull(record)
        assertEquals("ok", record.message)
    }

    @Test
    fun rejects_unknown_event_kind() {
        val line =
            """h |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=1 parent=- kind=UNKNOWN head="x" }|"""
        assertNull(parseLine(line))
    }

    @Test
    fun parses_browser_console_format_with_trailing_source_location() {
        val line =
            """kmpertrace-sample-app.uninstantiated.mjs:219 2025-12-06T21:50:29.849Z DEBUG Database Saved 3 activity events for user-123 |{ ts=2025-12-06T21:50:29.849Z lvl=debug log=Database trace=trace-1 span=span-1 parent=parent kind=LOG name="Database.saveActivity" dur=0 thread="unknown" svc=sample-app env=dev head="Saved 3 activity events for user-123" src_comp=Database src_op=saveActivity src_hint="Database.saveActivity" }| (wasm code):0"""
        val record = parseLine(line)
        assertNotNull(record)
        assertEquals("Saved 3 activity events for user-123", record.message)
        assertEquals("trace-1", record.traceId)
        assertEquals("span-1", record.spanId)
    }

    @Test
    fun cleans_stack_trace_logcat_prefixes() {
        val stackTrace = """
            1765068321.293 26055 26055 E Downloader: java.lang.IllegalStateException: Boom
            12-06 19:39:38.893 25580 25580 E Downloader:     at com.example.Clazz.method(Clazz.kt:10)
            E/Downloader:     at another.Line(Other.kt:20)
            plain line stays
        """.trimIndent()
        val line =
            "prefix |{ ts=2025-01-01T00:00:00Z lvl=error log=Api trace=trace-1 span=root parent=- kind=SPAN_END name=\"root\" dur=10 head=\"end\" stack_trace=\"$stackTrace\" }|"

        val record = parseLine(line)
        assertNotNull(record)
        val cleaned = record.rawFields["stack_trace"]!!.split('\n')
        assertEquals("java.lang.IllegalStateException: Boom", cleaned[0])
        assertEquals("    at com.example.Clazz.method(Clazz.kt:10)", cleaned[1])
        assertEquals("    at another.Line(Other.kt:20)", cleaned[2])
        assertEquals("plain line stays", cleaned[3])
    }

    @Test
    fun preserves_span_source_metadata_in_trace_nodes() {
        val line =
            """human |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=span-1 parent=- kind=SPAN_START name="root" dur=10 file="Repo.kt" line=42 fn="load" src_comp=Repo src_op=refresh src_hint="Repo.refresh" }|"""

        val traces = buildTraces(parseLines(listOf(line)))
        val span = traces.single().spans.single()
        assertEquals("Repo.kt", span.sourceFile)
        assertEquals(42, span.sourceLine)
        assertEquals("load", span.sourceFunction)
        assertEquals("Repo", span.sourceComponent)
        assertEquals("refresh", span.sourceOperation)
        assertEquals("Repo.refresh", span.sourceLocationHint)
    }

    @Test
    fun span_end_attributes_and_status_are_kept_on_records() {
        val line =
            """human |{ ts=2025-01-01T00:00:01Z lvl=error log=Api trace=trace-1 span=span-1 parent=- kind=SPAN_END name="root" dur=55 status="ERROR" err_type="IllegalStateException" err_msg="boom" a:custom="attr" }|"""

        val endRecord = parseLines(listOf(line)).single()
        assertEquals("ERROR", endRecord.rawFields["status"])
        assertEquals("IllegalStateException", endRecord.rawFields["err_type"])
        assertEquals("boom", endRecord.rawFields["err_msg"])
        assertEquals("attr", endRecord.rawFields["a:custom"])
    }

    @Test
    fun coalesces_wrapped_lines_with_stack_trace() {
        val lines = sequenceOf(
            "prefix |{ ts=2025-01-01T00:00:00Z lvl=error log=Api trace=trace-1 span=s1 parent=- kind=SPAN_END name=\"root\" dur=1 head=\"end\" stack_trace=\"java.lang.IllegalStateException: boom",
            "    at A.foo(A.kt:10)",
            "    at B.bar(B.kt:20)",
            " \" }| trailing"
        )

        val records = parseLines(lines)
        val record = records.single()
        assertTrue(record.rawFields.containsKey("stack_trace"))
    }

    @Test
    fun parses_log_without_structured_msg_using_colon_prefix() {
        val line = "LoggerX: hello world |{ ts=2025-01-01T00:00:00Z lvl=info }|"
        val record = parseLine(line)
        assertNotNull(record)
        assertEquals("LoggerX", record.loggerName)
        assertEquals("hello world", record.message)
        assertEquals(LogRecordKind.LOG, record.logRecordKind)
    }

    @Test
    fun parses_log_without_structured_msg_using_glyph_prefix() {
        val line = "🔍 ProfileViewModel: refreshAll |{ ts=2025-01-01T00:00:00Z lvl=debug trace=trace-7 span=span-7 }|"
        val record = parseLine(line)
        assertNotNull(record)
        assertEquals("ProfileViewModel", record.loggerName)
        assertEquals("refreshAll", record.message)
        assertEquals("trace-7", record.traceId)
        assertEquals("span-7", record.spanId)
    }

    @Test
    fun parses_log_without_ev_defaults_to_log_with_ts_level_prefix() {
        val line =
            "2025-01-01T00:00:00Z INFO Api refresh |{ ts=2025-01-01T00:00:00Z lvl=info trace=trace-9 span=span-9 log=Api head=\"refresh\" }|"
        val record = parseLine(line)
        assertNotNull(record)
        assertEquals(LogRecordKind.LOG, record.logRecordKind)
        assertEquals("Api", record.loggerName)
        assertEquals("refresh", record.message)
    }

    @Test
    fun parses_span_start_end_with_symbolic_human_prefix() {
        val startLine =
            "+++ ProfileRepository.loadProfile |{ ts=2025-01-01T00:00:00Z lvl=info trace=abc span=span-1 kind=SPAN_START name=\"ProfileRepository.loadProfile\" }|"
        val endLine =
            "--- ProfileRepository.loadProfile |{ ts=2025-01-01T00:00:01Z lvl=info trace=abc span=span-1 kind=SPAN_END name=\"ProfileRepository.loadProfile\" dur=10 }|"

        val start = parseLine(startLine)
        val end = parseLine(endLine)

        assertNotNull(start)
        assertNotNull(end)
        assertEquals("ProfileRepository.loadProfile", start!!.spanName)
        assertEquals(LogRecordKind.SPAN_START, start.logRecordKind)
        assertEquals("ProfileRepository.loadProfile", end!!.spanName)
        assertEquals(LogRecordKind.SPAN_END, end.logRecordKind)
        // Messages come from human prefix when missing in structured fields.
        assertEquals("+++ ProfileRepository.loadProfile", start.message)
        assertEquals("--- ProfileRepository.loadProfile", end.message)
    }

    @Test
    fun parses_logcat_human_prefix() {
        val line =
            """12-08 15:12:55.610 10801 10801 D Downloader: Downloading now |{ ts=2025-12-08T20:12:55.610039Z lvl=debug trace=abc span=def src=Downloader }|"""
        val record = parseLine(line)
        assertNotNull(record)
        assertEquals("Downloader", record.loggerName)
        assertEquals("Downloading now", record.message)
    }

    @Test
    fun strips_logcat_prefixes_from_multiline_messages() {
        val line = """
            1766334788.705 19358 19451 D SqliteNow: SafeSQLiteConnection.execSQL: CREATE TABLE app_settings_record
            1766334788.705 19358 19451 D SqliteNow: (
            1766334788.705 19358 19451 D SqliteNow: id TEXT NOT NULL
            1766334788.705 19358 19451 D SqliteNow: ); |{ ts=2025-01-01T00:00:00Z lvl=debug log=SqliteNow trace=abc span=def head="SafeSQLiteConnec" }|
        """.trimIndent()

        val record = parseLine(line)
        assertNotNull(record)
        assertEquals(
            "SafeSQLiteConnection.execSQL: CREATE TABLE app_settings_record\n(\nid TEXT NOT NULL\n);",
            record.message
        )
        assertTrue(record.message?.contains("19358") == false)
    }
}
