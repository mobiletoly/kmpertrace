package dev.goquick.kmpertrace.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StructuredSuffixFramerTest {
    @Test
    fun frames_multiline_structured_suffix_and_keeps_trailing() {
        val framer = StructuredSuffixFramer()
        assertFalse(framer.isOpenStructured())

        val part1 = """prefix |{ ts=2025-01-01T00:00:00Z lvl=info head="a""""
        val out1 = framer.feed(part1)
        assertEquals(emptyList(), out1)
        assertTrue(framer.isOpenStructured())

        val part2 = """b" }|trailing"""
        val out2 = framer.feed(part2)
        assertEquals(1, out2.size)
        assertEquals(
            """$part1
$part2""".substringBefore("trailing"),
            out2.single()
        )

        // trailing should remain buffered
        val out3 = framer.flush()
        assertEquals(listOf("trailing"), out3)
    }
}

