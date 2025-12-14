package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.parse.ParsedLogRecord
import dev.goquick.kmpertrace.parse.parseLine
import java.io.BufferedReader
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertTrue

class FollowModeTest {
    @Test
    fun streaming_input_emits_span_end_with_stack_trace() {
        // Simulate logcat splitting a multiline span end across multiple readLine() calls.
        val chunked = listOf(
            """prefix |{ ts=2025-01-01T00:00:00Z lvl=info log=Api trace=trace-1 span=s1 parent_span=- kind=SPAN_START name="root" dur=0 head="start" }|""",
            """prefix |{ ts=2025-01-01T00:00:01Z lvl=error log=Api trace=trace-1 span=s1 parent_span=- kind=SPAN_END name="root" dur=10 head="boom" status="ERROR" stack_trace="java.lang.IllegalStateException: boom""",
            """    at A.foo(A.kt:10)""",
            """    at B.bar(B.kt:20)""",
            """ " }|"""
        )

        val reader = BufferedReader(StringReader(chunked.joinToString("\n")))
        val records = ArrayDeque<ParsedLogRecord>()
        val pending = StringBuilder()

        while (true) {
            val line = reader.readLine() ?: break
            if (pending.isNotEmpty()) pending.append('\n')
            pending.append(line)

            val buffered = pending.toString()
            val lastOpen = buffered.lastIndexOf("|{")
            val lastClose = buffered.lastIndexOf("}|")
            if (lastOpen != -1 && lastClose != -1 && lastClose > lastOpen) {
                val candidate = buffered.substring(lastOpen, lastClose + 2)
                parseLine(candidate)?.let { records.add(it) }
                // If there is trailing data after the last closing brace, keep it.
                val trailing = buffered.substring(lastClose + 2).trimStart('\n')
                pending.clear()
                if (trailing.isNotEmpty()) pending.append(trailing)
            }
        }

        // Ensure we captured a span end with stack trace content.
        val end = records.firstOrNull { it.logRecordKind == dev.goquick.kmpertrace.parse.LogRecordKind.SPAN_END }
        assertTrue(end != null)
        val stack = end.rawFields["stack_trace"] ?: ""
        assertTrue(stack.isNotBlank())
    }
}
