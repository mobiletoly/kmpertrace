package dev.goquick.kmpertrace.cli.source

internal object SourceCommands {
    private val safeAndroidPackageRegex = Regex("^[A-Za-z0-9_.]+$")
    private val safeProcessNameRegex = Regex("^[A-Za-z0-9_.-]+$")

    fun buildAdbCommand(adbCmd: String?, adbPkg: String?): String {
        adbCmd?.let { return it }
        require(adbPkg != null) { "--adb-cmd or --adb-pkg is required when --source=adb" }
        require(safeAndroidPackageRegex.matches(adbPkg)) {
            "--adb-pkg must match ${safeAndroidPackageRegex.pattern} (got: $adbPkg)"
        }
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

    fun buildIosCommand(iosCmd: String?, iosProc: String?): String {
        iosCmd?.let { return it }
        require(iosProc != null) { "--ios-cmd or --ios-proc is required when --source=ios" }
        require(safeProcessNameRegex.matches(iosProc)) {
            "--ios-proc must match ${safeProcessNameRegex.pattern} (got: $iosProc)"
        }
        return """xcrun simctl spawn booted log stream --style syslog --predicate 'process == "$iosProc"'"""
    }
}

