package dev.goquick.kmpertrace.cli.source

import dev.goquick.kmpertrace.cli.usageError

internal object IosTargetResolver {
    enum class IosTarget { AUTO, SIM, DEVICE }

    data class BootedSimulator(val name: String, val udid: String)

    sealed interface Selection {
        data class Simulator(val udid: String) : Selection
        data class Device(val udid: String) : Selection
    }

    fun resolve(
        iosTarget: String?,
        iosUdid: String?,
        iosProc: String?,
        bootedSims: List<BootedSimulator>,
        deviceUdids: List<String>
    ): Selection {
        val target = parseTarget(iosTarget)

        val hasSim = bootedSims.isNotEmpty()
        val hasDevice = deviceUdids.isNotEmpty()

        val chosen = when (target) {
            IosTarget.SIM -> IosTarget.SIM
            IosTarget.DEVICE -> IosTarget.DEVICE
            IosTarget.AUTO -> {
                when {
                    hasSim && hasDevice ->
                        usageError("Both a booted iOS simulator and a connected device are available; pass --ios-target sim|device.")
                    hasSim -> IosTarget.SIM
                    hasDevice -> IosTarget.DEVICE
                    else -> usageError("No iOS sources found. Boot a simulator or connect a device.")
                }
            }
        }

        return when (chosen) {
            IosTarget.SIM -> {
                if (iosProc == null) usageError("--ios-proc is required when --ios-target=sim")
                if (!hasSim) usageError("No booted iOS simulators detected; boot a simulator or pass --ios-target device.")
                val udid =
                    iosUdid?.let { requested ->
                        val match = bootedSims.firstOrNull { it.udid == requested }
                        if (match == null) {
                            usageError(
                                "No booted simulator matches --ios-udid=$requested. Booted simulators:\n" +
                                    bootedSims.joinToString("\n") { "  ${it.name} (${it.udid})" }
                            )
                        }
                        requested
                    } ?: run {
                        if (bootedSims.size == 1) bootedSims.single().udid
                        else usageError(
                            "Multiple booted simulators detected; pass --ios-udid. Booted simulators:\n" +
                                bootedSims.joinToString("\n") { "  ${it.name} (${it.udid})" }
                        )
                    }
                Selection.Simulator(udid)
            }

            IosTarget.DEVICE -> {
                if (iosProc == null) usageError("--ios-proc is required when --ios-target=device")
                if (!hasDevice) usageError("No connected iOS devices detected; connect a device or pass --ios-target sim.")
                val udid =
                    iosUdid?.let { requested ->
                        if (!deviceUdids.contains(requested)) {
                            usageError(
                                "No connected device matches --ios-udid=$requested. Connected devices:\n" +
                                    deviceUdids.joinToString("\n") { "  $it" }
                            )
                        }
                        requested
                    } ?: run {
                        if (deviceUdids.size == 1) deviceUdids.single()
                        else usageError(
                            "Multiple connected iOS devices detected; pass --ios-udid. Connected devices:\n" +
                                deviceUdids.joinToString("\n") { "  $it" }
                        )
                    }
                Selection.Device(udid)
            }

            IosTarget.AUTO -> throw IllegalStateException("unreachable")
        }
    }

    private fun parseTarget(iosTarget: String?): IosTarget {
        val raw = (iosTarget ?: "auto").lowercase()
        return when (raw) {
            "auto" -> IosTarget.AUTO
            "sim" -> IosTarget.SIM
            "device" -> IosTarget.DEVICE
            else -> usageError("--ios-target must be one of: auto|sim|device (got: $iosTarget)")
        }
    }
}
