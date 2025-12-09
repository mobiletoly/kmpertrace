package dev.goquick.kmpertrace.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RawLogsTest {

    @Test
    fun skips_structured_lines() {
        val line =
            "t INFO Api |{ ts=2025-01-01T00:00:00Z lvl=info trace=abc span=def ev=LOG name=\"n\" }|"
        val evt = rawEventFromLine(line, RawLogLevel.DEBUG)
        assertNull(evt)
    }

    @Test
    fun parses_logcat_line_when_level_allows() {
        val line = "D/Foo: hello world"
        val evt = rawEventFromLine(line, RawLogLevel.DEBUG)
        assertNotNull(evt)
        assertEquals("D/Foo", evt.loggerName)
        assertEquals("debug", evt.rawFields["lvl"])
        assertEquals(line, evt.message)

        assertNull(rawEventFromLine(line, RawLogLevel.INFO)) // filtered out at higher min level
    }

    @Test
    fun parses_android_epoch_logcat_line() {
        val line = "1765253113.819  1219  1219 I trace.sampleapp: Late-enabling -Xcheck:jni"
        val evt = rawEventFromLine(line, RawLogLevel.DEBUG)
        assertNotNull(evt)
        assertEquals("trace.sampleapp", evt.loggerName)
        assertEquals("Late-enabling -Xcheck:jni", evt.message)
        assertEquals("info", evt.rawFields["lvl"])
        assertEquals("1765253113.819", evt.timestamp)
    }

    @Test
    fun parses_android_warn_epoch_line() {
        val line = "1765256062.678 16159 16179 W HWUI    : Failed to initialize 101010-2 format, error = EGL_SUCCESS"
        val evt = rawEventFromLine(line, RawLogLevel.ALL)
        assertNotNull(evt)
        assertEquals("warn", evt.rawFields["lvl"])
        assertEquals("HWUI", evt.loggerName)
        assertEquals("Failed to initialize 101010-2 format, error = EGL_SUCCESS", evt.message)
        assertEquals("1765256062.678", evt.timestamp)
    }

    @Test
    fun parses_android_month_day_logcat_line() {
        val line =
            "12-08 17:56:03.806  5330  5330 D ProfileRepository: ProfileRepository: loadProfile finished for user-123"
        val evt = rawEventFromLine(line, RawLogLevel.DEBUG)
        assertNotNull(evt)
        assertEquals("ProfileRepository", evt.loggerName)
        assertEquals("ProfileRepository: loadProfile finished for user-123", evt.message)
        assertEquals("debug", evt.rawFields["lvl"])
        assertEquals("12-08 17:56:03.806", evt.timestamp)
    }

    @Test
    fun skips_noise_lines() {
        val noise = "--------- beginning of main"
        assertNull(rawEventFromLine(noise, RawLogLevel.ALL))
        val noise2 = "streaming logcat for pid=1234 (pkg)"
        assertNull(rawEventFromLine(noise2, RawLogLevel.ALL))
    }

    @Test
    fun parses_ios_syslog_style_line() {
        val line =
            "2025-12-08 23:18:36.143963-0500  localhost powerd[333]: [com.apple.powerd:displayState] DesktopMode check on Battery 0"
        val evt = rawEventFromLine(line, RawLogLevel.DEBUG)
        assertNotNull(evt)
        assertEquals("powerd", evt.loggerName)
        assertEquals("DesktopMode check on Battery 0", evt.message)
        assertEquals("info", evt.rawFields["lvl"])
        assertEquals("2025-12-08 23:18:36.143963-0500", evt.timestamp)
        assertEquals("com.apple.powerd:displayState", evt.rawFields["subsystem"])
        assertEquals("333", evt.rawFields["pid"])
    }

    @Test
    fun parses_ios_compact_style_line_with_level() {
        val line = "12:34:56.789 MyApp[123:4567] <Info> [com.company:net] Fetching..."
        val evt = rawEventFromLine(line, RawLogLevel.DEBUG)
        assertNotNull(evt)
        assertEquals("MyApp", evt.loggerName)
        assertEquals("info", evt.rawFields["lvl"])
        assertEquals("12:34:56.789", evt.timestamp)
        assertEquals("com.company:net", evt.rawFields["subsystem"])
        assertEquals("Fetching...", evt.message)
    }

    @Test
    fun parses_android_studio_logcat_line() {
        val line =
            "2025-12-09 01:29:53.305  5510-5510  ziparchive              dev.goquick.kmpertrace.sampleapp     W  Unable to open '/data/app/.../base.dm': No such file or directory"
        val evt = rawEventFromLine(line, RawLogLevel.ALL)
        assertNotNull(evt)
        assertEquals("warn", evt.rawFields["lvl"])
        assertEquals("ziparchive", evt.loggerName)
        assertEquals("Unable to open '/data/app/.../base.dm': No such file or directory", evt.message)
        assertEquals("2025-12-09 01:29:53.305", evt.timestamp)
    }
}
