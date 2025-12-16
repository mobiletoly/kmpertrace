package dev.goquick.kmpertrace.cli.source

import dev.goquick.kmpertrace.cli.cliError
import dev.goquick.kmpertrace.cli.usageError
import java.io.BufferedReader

internal object IosDiscovery {
    enum class IosSourceKind { SIMULATOR, DEVICE }

    data class ResolvedIosSource(val kind: IosSourceKind, val command: String)

    fun resolveIosSource(
        iosCmd: String?,
        iosProc: String?,
        iosTarget: String?,
        iosUdid: String?
    ): ResolvedIosSource {
        iosCmd?.let { return ResolvedIosSource(IosSourceKind.SIMULATOR, iosCmd) }
        if (iosProc == null) {
            usageError("--ios-proc is required for iOS sources (unless --ios-cmd is set)")
        }

        val bootedSims = listBootedSimulators().map { IosTargetResolver.BootedSimulator(it.name, it.udid) }
        val deviceUdids = listConnectedDeviceUdids()
        return when (val sel = IosTargetResolver.resolve(iosTarget, iosUdid, iosProc, bootedSims, deviceUdids)) {
            is IosTargetResolver.Selection.Simulator -> {
                ResolvedIosSource(
                    kind = IosSourceKind.SIMULATOR,
                    command = SourceCommands.buildIosSimCommand(iosProc = iosProc, simUdid = sel.udid)
                )
            }

            is IosTargetResolver.Selection.Device ->
                ResolvedIosSource(
                    kind = IosSourceKind.DEVICE,
                    command = SourceCommands.buildIosDeviceCommand(deviceUdid = sel.udid, iosProc = iosProc)
                )
        }
    }

    private fun listBootedSimulators(): List<BootedSimulator> {
        val out = runAndCollectLines("xcrun simctl list devices booted")
        val regex = Regex("""^\s*(.+?)\s+\(([0-9A-Fa-f-]{36})\)\s+\(Booted\)\s*$""")
        return out.mapNotNull { line ->
            val m = regex.matchEntire(line) ?: return@mapNotNull null
            BootedSimulator(name = m.groupValues[1], udid = m.groupValues[2])
        }
    }

    private data class BootedSimulator(val name: String, val udid: String)

    private fun listConnectedDeviceUdids(): List<String> {
        val out = runAndCollectLines("idevice_id -l")
        return out.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
    }

    private fun runAndCollectLines(command: String): List<String> {
        val process = ProcessBuilder(listOf("sh", "-c", command))
            .redirectErrorStream(true)
            .start()
        val lines = process.inputStream.bufferedReader().use(BufferedReader::readLines)
        val exit = process.waitFor()
        if (exit != 0) {
            cliError("Command failed ($exit): $command\n${lines.joinToString("\n")}")
        }
        return lines
    }
}
