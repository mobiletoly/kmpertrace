package dev.goquick.kmpertrace.cli

import com.github.ajalt.clikt.core.UsageError
import dev.goquick.kmpertrace.cli.ansi.AnsiMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CliParsingTest {
    @Test
    fun parseAnsiMode_accepts_on_off_auto() {
        assertEquals(AnsiMode.ON, parseAnsiMode("on"))
        assertEquals(AnsiMode.OFF, parseAnsiMode("off"))
        assertEquals(AnsiMode.AUTO, parseAnsiMode(null))
    }

    @Test
    fun parseAnsiMode_rejects_invalid() {
        assertFailsWith<UsageError> { parseAnsiMode("maybe") }
    }

    @Test
    fun parseTimeFormat_accepts_full_and_time_only() {
        assertEquals(TimeFormat.FULL, parseTimeFormat("full"))
        assertEquals(TimeFormat.TIME_ONLY, parseTimeFormat("time"))
        assertEquals(TimeFormat.TIME_ONLY, parseTimeFormat(null))
    }

    @Test
    fun parseTimeFormat_rejects_invalid() {
        assertFailsWith<UsageError> { parseTimeFormat("nope") }
    }

    @Test
    fun resolveWidth_handles_auto_defaults_and_numbers() {
        assertEquals(null to true, resolveWidth(null, autoByDefault = true))
        assertEquals(null to false, resolveWidth(null, autoByDefault = false))
        assertEquals(null to true, resolveWidth("auto", autoByDefault = false))
        assertEquals(null to false, resolveWidth("unlimited", autoByDefault = false))
        assertEquals(null to false, resolveWidth("0", autoByDefault = false))
        assertEquals(80 to false, resolveWidth("80", autoByDefault = false))
    }

    @Test
    fun validateMaxWidth_accepts_known_values_and_rejects_bad() {
        validateMaxWidth("auto")
        validateMaxWidth("unlimited")
        validateMaxWidth("0")
        validateMaxWidth("10")
        assertFailsWith<IllegalArgumentException> { validateMaxWidth("-1") }
        assertFailsWith<IllegalArgumentException> { validateMaxWidth("bad") }
    }
}
