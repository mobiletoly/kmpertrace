package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.cli.source.IdeviceSyslogLineProcessor
import dev.goquick.kmpertrace.cli.source.IdeviceSyslogPrefixStripper
import dev.goquick.kmpertrace.parse.parseLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IdeviceSyslogLineProcessorTest {

    @Test
    fun normalizes_idevicesyslog_prefix_to_syslog_style() {
        val line =
            """Dec 15 01:39:46.970278 SampleApp(SampleApp.debug.dylib)[10612] <Notice>: 🔍 Hello |{ ts=2025-12-15T06:39:46.970278Z lvl=debug }|"""
        val normalized = IdeviceSyslogPrefixStripper.normalizeToSyslogStyle(line)
        assertTrue(normalized.contains("01:39:46.970278"))
        assertTrue(normalized.contains("SampleApp[10612]:"))
        assertTrue(normalized.contains("[SampleApp.debug.dylib]"))
        assertTrue(normalized.contains("<Notice>"))
        assertTrue(normalized.endsWith("""🔍 Hello |{ ts=2025-12-15T06:39:46.970278Z lvl=debug }|"""))
    }

    @Test
    fun keeps_multiline_structured_records_in_device_mode_when_raw_logs_disabled() {
        val processor = IdeviceSyslogLineProcessor(iosProc = "SampleApp")

        val lines = listOf(
            // Start of structured suffix, stack trace opens but doesn't close.
            """Dec 15 01:39:46.970278 SampleApp(SampleApp.debug.dylib)[10612] <Notice>: ❌ Downloader: --- Downloader.DownloadA |{ ts=2025-12-15T06:39:46.919486Z lvl=error trace=0f89d69311de9211 span=783c26a493db2329 parent=fa23c337562453a8 kind=SPAN_END name="Downloader.DownloadA" stack_trace="first""",
            // Continuation line without `|{` (must be retained).
            """Dec 15 01:39:46.970407 SampleApp(SampleApp.debug.dylib)[10612] <Notice>: second""",
            // Close the quoted stack trace and structured suffix.
            """Dec 15 01:39:46.970468 SampleApp(SampleApp.debug.dylib)[10612] <Notice>: third"}|"""
        )

        val processed = lines.mapNotNull(processor::process)
        assertEquals(3, processed.size)
        assertTrue(processed[0].contains("❌ Downloader: --- Downloader.DownloadA |{"))
        assertTrue(processed[1].contains("second"))
        assertTrue(processed[2].contains("""third"}|"""))

        val parsed = parseLines(processed)
        assertEquals(1, parsed.size)
        val record = parsed.single()

        val stack = record.rawFields["stack_trace"]
        assertNotNull(stack)
        assertTrue(stack.contains("first\nsecond\nthird"))
    }

    @Test
    fun keeps_prefixless_continuation_lines_inside_open_structured_frame() {
        val processor = IdeviceSyslogLineProcessor(iosProc = "SampleApp")

        val processed =
            listOf(
                """Dec 15 01:39:46.970278 SampleApp(SampleApp.debug.dylib)[10612] <Notice>: ❌ Downloader: --- Downloader.DownloadA |{ ts=2025-12-15T06:39:46.919486Z lvl=error trace=0f89d69311de9211 span=783c26a493db2329 parent=fa23c337562453a8 kind=SPAN_END name="Downloader.DownloadA" stack_trace="first""",
                // Some transports emit stack continuations without the process header; we must keep them.
                """second""",
                """third"}|"""
            ).mapNotNull(processor::process)

        val parsed = parseLines(processed)
        assertEquals(1, parsed.size)
        val stack = parsed.single().rawFields["stack_trace"]
        assertNotNull(stack)
        assertTrue(stack.contains("first\nsecond\nthird"))
    }

    @Test
    fun keeps_timestamps_for_non_structured_device_lines() {
        val processor = IdeviceSyslogLineProcessor(iosProc = "SampleApp")
        val out =
            processor.process(
                "Dec 15 11:48:28.972604 SampleApp(UIKitCore)[11710] <Notice>: Sending UIEvent type: 0; subtype: 0; to windows: 1"
            )
        assertNotNull(out)
        assertTrue(out.contains("11:48:28.972604"))
        assertTrue(out.contains("SampleApp[11710]:"))
        assertTrue(out.contains("[UIKitCore]"))
        assertTrue(out.contains("<Notice>"))
        assertTrue(out.endsWith("Sending UIEvent type: 0; subtype: 0; to windows: 1"))
    }
}
