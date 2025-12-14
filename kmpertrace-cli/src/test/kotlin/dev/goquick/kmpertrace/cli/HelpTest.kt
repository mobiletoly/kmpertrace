package dev.goquick.kmpertrace.cli

import kotlin.test.Test
import kotlin.test.assertTrue

class HelpTest {
    @Test
    fun help_mentions_current_raw_cycle_order() {
        val text = renderHelp(colorize = false)
        assertTrue(
            text.contains("r : cycle raw logs (off → all → debug → info → warn → error → off)"),
            "help text should match nextRawLevel behavior:\n$text"
        )
    }
}

