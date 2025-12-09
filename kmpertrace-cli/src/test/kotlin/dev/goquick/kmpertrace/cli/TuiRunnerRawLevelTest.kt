package dev.goquick.kmpertrace.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class TuiRunnerRawLevelTest {
    @Test
    fun cycles_raw_levels_off_all_debug_info_warn_error_off() {
        val seq = mutableListOf<RawLogLevel>()
        var enabled = false
        var level = RawLogLevel.OFF
        repeat(6) {
            level = nextRawLevel(enabled, level)
            enabled = level != RawLogLevel.OFF
            seq += level
        }
        // Sequence after six presses starting from off
        assertEquals(
            listOf(
                RawLogLevel.ALL,
                RawLogLevel.DEBUG,
                RawLogLevel.INFO,
                RawLogLevel.WARN,
                RawLogLevel.ERROR,
                RawLogLevel.OFF
            ),
            seq
        )
    }
}
