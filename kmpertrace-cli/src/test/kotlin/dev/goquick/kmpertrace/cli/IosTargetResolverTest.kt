package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.cli.source.IosTargetResolver
import com.github.ajalt.clikt.core.UsageError
import kotlin.test.Test
import kotlin.test.assertEquals

class IosTargetResolverTest {

    @Test
    fun auto_errors_when_both_sim_and_device_present() {
        try {
            IosTargetResolver.resolve(
                iosTarget = "auto",
                iosUdid = null,
                iosProc = "SampleApp",
                bootedSims = listOf(IosTargetResolver.BootedSimulator("iPhone", "00000000-0000-0000-0000-000000000000")),
                deviceUdids = listOf("DEVICEUDID")
            )
            throw AssertionError("expected auto target to require explicit --ios-target when both sources exist")
        } catch (_: UsageError) {
            // ok (error(...) throws IllegalStateException)
        }
    }

    @Test
    fun sim_requires_ios_proc() {
        try {
            IosTargetResolver.resolve(
                iosTarget = "sim",
                iosUdid = null,
                iosProc = null,
                bootedSims = listOf(IosTargetResolver.BootedSimulator("iPhone", "00000000-0000-0000-0000-000000000000")),
                deviceUdids = emptyList()
            )
            throw AssertionError("expected --ios-proc to be required for simulator")
        } catch (_: UsageError) {
            // ok
        }
    }

    @Test
    fun sim_picks_only_booted_simulator_when_single() {
        val res =
            IosTargetResolver.resolve(
                iosTarget = "auto",
                iosUdid = null,
                iosProc = "SampleApp",
                bootedSims = listOf(IosTargetResolver.BootedSimulator("iPhone", "00000000-0000-0000-0000-000000000000")),
                deviceUdids = emptyList()
            )
        assertEquals(IosTargetResolver.Selection.Simulator("00000000-0000-0000-0000-000000000000"), res)
    }

    @Test
    fun device_picks_only_connected_device_when_single() {
        val res =
            IosTargetResolver.resolve(
                iosTarget = "auto",
                iosUdid = null,
                iosProc = "SampleApp",
                bootedSims = emptyList(),
                deviceUdids = listOf("DEVICEUDID")
            )
        assertEquals(IosTargetResolver.Selection.Device("DEVICEUDID"), res)
    }

    @Test
    fun device_requires_ios_proc() {
        try {
            IosTargetResolver.resolve(
                iosTarget = "device",
                iosUdid = null,
                iosProc = null,
                bootedSims = emptyList(),
                deviceUdids = listOf("DEVICEUDID")
            )
            throw AssertionError("expected --ios-proc to be required for device")
        } catch (_: UsageError) {
            // ok
        }
    }

    @Test
    fun device_errors_when_no_devices_connected() {
        try {
            IosTargetResolver.resolve(
                iosTarget = "device",
                iosUdid = null,
                iosProc = "SampleApp",
                bootedSims = emptyList(),
                deviceUdids = emptyList()
            )
            throw AssertionError("expected a clear 'no devices' error")
        } catch (_: UsageError) {
            // ok
        }
    }

    @Test
    fun sim_errors_when_no_booted_simulators() {
        try {
            IosTargetResolver.resolve(
                iosTarget = "sim",
                iosUdid = null,
                iosProc = "SampleApp",
                bootedSims = emptyList(),
                deviceUdids = emptyList()
            )
            throw AssertionError("expected a clear 'no simulators' error")
        } catch (_: UsageError) {
            // ok
        }
    }
}
