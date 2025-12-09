package dev.goquick.kmpertrace.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTimestampTest {
    @Test
    fun converts_epoch_seconds_to_time_only() {
        val ts = "1765254324.631" // 2025-12-09T04:25:24.631Z
        assertEquals("04:25:24.631", formatTimestamp(ts, TimeFormat.TIME_ONLY))
        assertEquals("2025-12-09T04:25:24.631Z", formatTimestamp(ts, TimeFormat.FULL))
    }

    @Test
    fun extracts_time_from_logcat_month_day() {
        val ts = "12-08 17:56:03.806"
        assertEquals("17:56:03.806", formatTimestamp(ts, TimeFormat.TIME_ONLY))
        assertEquals(ts, formatTimestamp(ts, TimeFormat.FULL))
    }
}
