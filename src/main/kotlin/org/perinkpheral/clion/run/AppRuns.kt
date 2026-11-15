// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.run

import org.perinkpheral.clion.settings.PerinkpheralSettings
import org.perinkpheral.clion.simulator.Simulator
import org.perinkpheral.core.app.Applications
import org.perinkpheral.core.build.AppBuilder
import org.perinkpheral.core.build.BuildError
import org.perinkpheral.core.build.Sdk
import org.perinkpheral.core.device.DeviceClient
import org.perinkpheral.core.simulator.SimulatorNetwork
import java.io.File

/** What the run configurations and the actions do: the core's work, in the IDE's own process,
 * its words in a Run console. */
object AppRuns {
    fun builder(handler: TaskProcessHandler): AppBuilder {
        val settings = PerinkpheralSettings.get()
        return AppBuilder(Sdk(File(settings.sdkPath)), File(settings.wasiSdkPath)) { handler.say(it) }
    }

    private fun device(): DeviceClient = DeviceClient(PerinkpheralSettings.get().deviceHost)

    /** Builds the application, puts it on the device, restarts the device and opens it. */
    fun onDevice(handler: TaskProcessHandler, appDir: File): Int {
        val name = Applications.toDevice(builder(handler), device(), appDir, { handler.say(it) }) { handler.isCancelled }
        handler.say("$name is on the screen")
        return 0
    }

    /** Builds the application into the simulator's state and runs the simulator until stopped. */
    fun inSimulator(handler: TaskProcessHandler, appDir: File, mocks: String): Int {
        val binary = Simulator.binary() ?: throw BuildError("The simulator runs on macOS and Linux only.")
        val settings = PerinkpheralSettings.get()
        val state = File(Simulator.home(), Applications.nameOf(appDir))
        val name = Applications.toSimulator(builder(handler), appDir, state)
        val command = mutableListOf(binary.path, "--apps", File(state, "apps").path, "--app", name, "--state", state.path,
            "--port", Simulator.PORT.toString(), "--language", settings.simulatorLanguage,
            "--font", settings.simulatorFont, "--fetcher-port", SimulatorNetwork.port().toString())
        if (Simulator.hasGlyphs()) command += listOf("--glyphs", Simulator.glyphsFolder().path)
        if (mocks.isNotBlank()) command += listOf("--mocks", mocks)
        return handler.runChild(command)
    }

    /** Sends a firmware image to the device, which installs it and restarts. */
    fun flash(handler: TaskProcessHandler, image: File): Int {
        handler.say("uploading ${image.name}, ${image.length()} bytes")
        handler.say(Applications.flash(device(), image, { handler.say(it) }) { handler.isCancelled })
        return 0
    }
}
