package dev.goquick.kmpertrace.cli.source

import dev.goquick.kmpertrace.cli.usageError

internal object SourceCommands {
    private val safeAndroidPackageRegex = Regex("^[A-Za-z0-9_.]+$")
    private val safeProcessNameRegex = Regex("^[A-Za-z0-9_.-]+$")
    private val safeUdidRegex = Regex("^[0-9A-Fa-f-]{8,64}$")

    fun buildAdbCommand(adbCmd: String?, adbPkg: String?): String {
        adbCmd?.let { return it }
        if (adbPkg == null) usageError("--adb-cmd or --adb-pkg is required when --source=adb")
        if (!safeAndroidPackageRegex.matches(adbPkg)) {
            usageError("--adb-pkg must match ${safeAndroidPackageRegex.pattern} (got: $adbPkg)")
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

    fun buildIosSimCommand(iosProc: String, simUdid: String): String {
        if (!safeProcessNameRegex.matches(iosProc)) {
            usageError("--ios-proc must match ${safeProcessNameRegex.pattern} (got: $iosProc)")
        }
        if (!safeUdidRegex.matches(simUdid)) {
            usageError("--ios-udid must match ${safeUdidRegex.pattern} (got: $simUdid)")
        }
        return """exec xcrun simctl spawn $simUdid log stream --style syslog --predicate 'process == "$iosProc"'"""
    }

    fun buildIosDeviceCommand(deviceUdid: String, iosProc: String): String {
        if (!safeUdidRegex.matches(deviceUdid)) {
            usageError("--ios-udid must match ${safeUdidRegex.pattern} (got: $deviceUdid)")
        }
        if (!safeProcessNameRegex.matches(iosProc)) {
            usageError("--ios-proc must match ${safeProcessNameRegex.pattern} (got: $iosProc)")
        }
        return "exec idevicesyslog -u $deviceUdid -p $iosProc --no-colors"
    }
}
