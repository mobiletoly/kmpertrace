package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.cli.source.Sources
import dev.goquick.kmpertrace.cli.source.SourceCommands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourcesTest {

    @Test
    fun resolve_prefers_explicit_source() {
        val src = Sources.resolve("file", filePresent = true, adbCmd = null, adbPkg = null, iosCmd = null, iosProc = null)
        assertEquals("file", src)
    }

    @Test
    fun resolve_infers_adb_when_pkg_present() {
        val src = Sources.resolve(null, filePresent = false, adbCmd = null, adbPkg = "pkg", iosCmd = null, iosProc = null)
        assertEquals("adb", src)
    }

    @Test
    fun build_adb_command_waits_for_pid_and_reattaches() {
        val cmd = SourceCommands.buildAdbCommand(adbCmd = null, adbPkg = "com.example.app")
        assertTrue(cmd.contains("pidof -s com.example.app"))
        assertTrue(cmd.contains("waiting for com.example.app to start"))
        assertTrue(cmd.contains("streaming logcat for pid="))
        assertTrue(cmd.contains("pid ${'$'}pid exited; restarting when app returns"))
    }

    @Test
    fun build_adb_command_rejects_unsafe_pkg() {
        try {
            SourceCommands.buildAdbCommand(adbCmd = null, adbPkg = "com.example.app; rm -rf /")
            throw AssertionError("expected unsafe --adb-pkg to be rejected")
        } catch (_: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun build_ios_command_rejects_unsafe_process_name() {
        try {
            SourceCommands.buildIosCommand(iosCmd = null, iosProc = "MyApp\"; rm -rf /")
            throw AssertionError("expected unsafe --ios-proc to be rejected")
        } catch (_: IllegalArgumentException) {
            // ok
        }
    }
}
