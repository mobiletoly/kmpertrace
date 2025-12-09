package dev.goquick.kmpertrace.analysis

import kotlin.test.Test
import kotlin.test.assertEquals

class ChunkAssemblerTest {

    @Test
    fun assembles_three_chunks() {
        val asm = ChunkAssembler()
        val first = "abc123 first part | |{ ts=... } (abc123:kmpert...)"
        val mid = "middle text (abc123:kmpert...)"
        val last = "tail }| (abc123:kmpert!)"

        val out1 = asm.feed(first)
        val out2 = asm.feed(mid)
        val out3 = asm.feed(last)

        val combined = (out1 + out2 + out3)
        assertEquals(1, combined.size)
        assertEquals("abc123 first part | |{ ts=... } middle text tail }|", combined.first())
    }

    @Test
    fun passes_through_non_chunked() {
        val asm = ChunkAssembler()
        val line = "plain line"
        assertEquals(listOf(line), asm.feed(line))
    }
}
