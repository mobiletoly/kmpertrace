package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.cli.source.Sources
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
        val cmd = SourcesTestHelper.buildAdbCmd("com.example.app")
        assertTrue(cmd.contains("pidof -s com.example.app"))
        assertTrue(cmd.contains("waiting for com.example.app to start"))
        assertTrue(cmd.contains("streaming logcat for pid="))
        assertTrue(cmd.contains("pid ${'$'}pid exited; restarting when app returns"))
    }
}

private object SourcesTestHelper {
    fun buildAdbCmd(pkg: String): String {
        val method = Sources::class.java.getDeclaredMethod(
            "buildAdbCommand",
            String::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(Sources, null, pkg) as String
    }
}
