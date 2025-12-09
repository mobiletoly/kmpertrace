package dev.goquick.kmpertrace.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HeadPreferenceTest {
    @Test
    fun prefers_head_when_human_message_differs() {
        val line =
            "1765257462.958 22999 22999 I ProfileViewModel: ProfileViewModel: starting now |{ ts=2025-12-09T05:17:42.958222Z lvl=info head=\"starting now\" src=ProfileViewModel svc=sample-app thread=\"main\" }|"
        val evt = parseLine(line)
        assertNotNull(evt)
        assertEquals("starting now", evt.message)
    }
}
