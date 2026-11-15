// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.run

import org.perinkpheral.clion.build.AppBuilder
import org.perinkpheral.clion.build.BuildError
import org.perinkpheral.clion.build.Json
import org.perinkpheral.clion.build.Sdk
import org.perinkpheral.clion.device.DeviceClient
import org.perinkpheral.clion.settings.PerinkpheralSettings
import org.perinkpheral.clion.simulator.Simulator
import org.perinkpheral.clion.simulator.SimulatorNetwork
import java.io.File
import java.nio.file.Files

/** What the run configurations and the actions do, in the plugin's own process. */
object AppRuns {
    fun builder(handler: TaskProcessHandler): AppBuilder {
        val settings = PerinkpheralSettings.get()
        return AppBuilder(Sdk(File(settings.sdkPath)), File(settings.wasiSdkPath)) { handler.say(it) }
    }

    /** Builds the application, puts it on the device, restarts the device and opens it. */
    fun onDevice(handler: TaskProcessHandler, appDir: File): Int {
        val host = PerinkpheralSettings.get().deviceHost
        val built = Files.createTempDirectory("perinkpheral-app").toFile()
        try {
            val name = builder(handler).build(appDir, built)
            val client = DeviceClient(host)
            // The manifest last: until it is there the device does not list the application,
            // and by then everything it needs is.
            val data = built.listFiles().orEmpty().map { it.name }.filter { it != "app.wasm" && it != "manifest.json" }.sorted()
            for (file in listOf("app.wasm") + data + listOf("manifest.json")) {
                if (handler.isCancelled) return 130
                client.uploadAppFile(name, file, File(built, file).readBytes())
            }
            client.restartInto(name)
            handler.say("uploaded $name to $host, restarting into it")
            client.waitUntilBack { handler.isCancelled }
            // The device starts with its default application when one is chosen: the one just
            // uploaded is opened by name.
            client.open(name)
            handler.say("$name is on the screen")
            return 0
        } finally {
            built.deleteRecursively()
        }
    }

    /** Builds the application into the simulator's state and runs the simulator until stopped. */
    fun inSimulator(handler: TaskProcessHandler, appDir: File, mocks: String): Int {
        val binary = Simulator.binary() ?: throw BuildError("The simulator runs on macOS and Linux only.")
        val settings = PerinkpheralSettings.get()
        val name = (Json.parse(File(appDir, "manifest.json").readText()) as? Map<*, *>)?.get("name") as? String
            ?: throw BuildError("${appDir.path}/manifest.json has no name")
        val state = File(Simulator.home(), name)
        val apps = File(state, "apps")
        val built = Files.createTempDirectory("perinkpheral-app").toFile()
        try {
            builder(handler).build(appDir, built)
            // The package goes over what is there: the files the application wrote stay, as they do
            // on the device when it is updated.
            built.copyRecursively(File(apps, name), overwrite = true)
        } finally {
            built.deleteRecursively()
        }
        val command = mutableListOf(binary.path, "--apps", apps.path, "--app", name, "--state", state.path,
            "--port", Simulator.PORT.toString(), "--language", settings.simulatorLanguage,
            "--font", settings.simulatorFont, "--fetcher-port", SimulatorNetwork.port().toString())
        if (Simulator.hasGlyphs()) command += listOf("--glyphs", Simulator.glyphsFolder().path)
        if (mocks.isNotBlank()) command += listOf("--mocks", mocks)
        return handler.runChild(command)
    }

    /** Sends a firmware image to the device, which installs it and restarts. */
    fun flash(handler: TaskProcessHandler, image: File): Int {
        val client = DeviceClient(PerinkpheralSettings.get().deviceHost)
        handler.say("uploading ${image.name}, ${image.length()} bytes")
        handler.say(client.flash(image.readBytes()).trim())
        val status = Json.parse(client.waitUntilBack { handler.isCancelled }) as? Map<*, *>
        handler.say("back: firmware ${status?.get("firmware")} in ${status?.get("slot")}" +
            if (status?.get("on_probation") == true) ", on probation" else "")
        return 0
    }
}
