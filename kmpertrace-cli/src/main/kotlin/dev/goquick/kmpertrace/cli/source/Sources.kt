package dev.goquick.kmpertrace.cli.source

import java.io.BufferedReader

object Sources {
    fun resolve(
        sourceOpt: String?,
        filePresent: Boolean,
        adbCmd: String?,
        adbPkg: String?,
        iosCmd: String?,
        iosProc: String?
    ): String {
        val explicit = sourceOpt?.lowercase()
        val inferred = when {
            explicit != null -> explicit
            filePresent -> "file"
            adbCmd != null || adbPkg != null -> "adb"
            iosCmd != null || iosProc != null -> "ios"
            else -> "stdin"
        }
        require(inferred in setOf("file", "adb", "ios", "stdin")) {
            "Invalid --source value: $explicit (use file|adb|ios|stdin)"
        }
        if (inferred == "file" && !filePresent) {
            error("--source=file requires --file")
        }
        return inferred
    }

    fun readerFor(
        resolvedSource: String,
        filePath: java.nio.file.Path?,
        adbCmd: String?,
        adbPkg: String?,
        iosCmd: String?,
        iosProc: String?
    ): BufferedReader = when (resolvedSource) {
        "stdin" -> System.`in`.bufferedReader()
        "file" -> filePath!!.toFile().bufferedReader()
        "adb" -> startProcessReader(buildAdbCommand(adbCmd, adbPkg))
        "ios" -> startProcessReader(buildIosCommand(iosCmd, iosProc))
        else -> throw IllegalStateException("Unknown source: $resolvedSource")
    }

    private fun buildAdbCommand(adbCmd: String?, adbPkg: String?): String {
        adbCmd?.let { return it }
        require(adbPkg != null) { "--adb-cmd or --adb-pkg is required when --source=adb" }
        // Wait for the process to appear and reattach if it restarts.
        return """
            while true; do
              pid=""
              while [ -z "${'$'}pid" ]; do
                pid=$(adb shell pidof -s $adbPkg | tr -d '\r')
                if [ -z "${'$'}pid" ]; then
                  echo "waiting for $adbPkg to start..." >&2
                  sleep 1
                fi
              done
              echo "streaming logcat for pid=${'$'}pid ($adbPkg)" >&2
              adb logcat -v epoch --pid=${'$'}pid &
              logcat_pid=${'$'}!
              while true; do
                sleep 1
                cur=$(adb shell pidof -s $adbPkg | tr -d '\r')
                if [ -z "${'$'}cur" ] || [ "${'$'}cur" != "${'$'}pid" ]; then
                  echo "pid ${'$'}pid exited; restarting when app returns..." >&2
                  kill ${'$'}logcat_pid 2>/dev/null
                  wait ${'$'}logcat_pid 2>/dev/null
                  break
                fi
              done
            done
        """.trimIndent()
    }

    private fun buildIosCommand(iosCmd: String?, iosProc: String?): String {
        iosCmd?.let { return it }
        require(iosProc != null) { "--ios-cmd or --ios-proc is required when --source=ios" }
        return """xcrun simctl spawn booted log stream --style syslog --predicate 'process == "$iosProc"'"""
    }

    private fun startProcessReader(command: String): BufferedReader {
        val process = ProcessBuilder(listOf("sh", "-c", command))
            .redirectErrorStream(true)
            .start()
        return process.inputStream.bufferedReader()
    }
}
