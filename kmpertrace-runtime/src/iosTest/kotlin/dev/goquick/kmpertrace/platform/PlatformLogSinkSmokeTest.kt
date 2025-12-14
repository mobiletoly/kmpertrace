package dev.goquick.kmpertrace.platform

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformLogSinkSmokeTest {
    @Test
    fun emitPossiblyChunked_with_percent_and_long_line_does_not_crash() {
        val long = "prefix 100% " + "x".repeat(950)

        // This test intentionally exercises the real NSLog path.
        // If it segfaults, Gradle will report a crashed test process.
        PlatformLogSink.emitPossiblyChunked(long)
        assertTrue(true)
    }
}
