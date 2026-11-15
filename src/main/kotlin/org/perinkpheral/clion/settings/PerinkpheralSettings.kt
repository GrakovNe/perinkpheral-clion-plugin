// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.io.File

/** Paths of the toolchain and the address of the device. Stored per IDE installation. */
@Service(Service.Level.APP)
@State(name = "PerinkpheralSettings", storages = [Storage("perinkpheral.xml")])
class PerinkpheralSettings : PersistentStateComponent<PerinkpheralSettings.State> {

    class State {
        /** Checkout of the perinkpheral repository: contains sdk/ and tools/. */
        var sdkPath: String = ""

        /** Unpacked wasi-sdk: contains bin/clang. */
        var wasiSdkPath: String = ""

        /** Host name or IP address of the device. */
        var deviceHost: String = ""

        /** The simulated device's language: "en", "ru", ... (sdk/languages.json). */
        var simulatorLanguage: String = "en"

        /** The simulated device's font: "departure" or "jersey". */
        var simulatorFont: String = "departure"
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        state = loaded
    }

    val sdkPath: String get() = state.sdkPath
    val wasiSdkPath: String get() = state.wasiSdkPath
    val deviceHost: String get() = state.deviceHost
    val simulatorLanguage: String get() = state.simulatorLanguage.ifBlank { "en" }
    val simulatorFont: String get() = state.simulatorFont.ifBlank { "departure" }

    fun tool(relativePath: String): File = File(state.sdkPath, relativePath)

    val isSdkValid: Boolean get() = tool("sdk/host_api.h").isFile && tool("sdk/lib/manifest_limits.h").isFile
    val isWasiSdkValid: Boolean get() = File(state.wasiSdkPath, "bin/clang").isFile || File(state.wasiSdkPath, "bin/clang.exe").isFile


    companion object {
        fun get(): PerinkpheralSettings =
            ApplicationManager.getApplication().getService(PerinkpheralSettings::class.java)

        /** Default location for components downloaded by the plugin. */
        fun managedDirectory(): File = File(System.getProperty("user.home"), ".perinkpheral")
    }
}
