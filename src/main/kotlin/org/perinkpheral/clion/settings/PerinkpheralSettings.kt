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

        /** Python 3 interpreter that runs the SDK tools. */
        var pythonPath: String = "python3"
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        state = loaded
    }

    val sdkPath: String get() = state.sdkPath
    val wasiSdkPath: String get() = state.wasiSdkPath
    val deviceHost: String get() = state.deviceHost
    val pythonPath: String get() = state.pythonPath.ifBlank { "python3" }

    fun tool(relativePath: String): File = File(state.sdkPath, relativePath)

    val isSdkValid: Boolean get() = tool("tools/mkapp/mkapp.py").isFile && tool("sdk/host_api.h").isFile
    val isWasiSdkValid: Boolean get() = File(state.wasiSdkPath, "bin/clang").isFile || File(state.wasiSdkPath, "bin/clang.exe").isFile

    /** Environment for the SDK tools. */
    fun toolEnvironment(): Map<String, String> = buildMap {
        if (state.wasiSdkPath.isNotBlank()) put("WASI_SDK_PATH", state.wasiSdkPath)
        if (state.deviceHost.isNotBlank()) put("PERINKPHERAL_HOST", state.deviceHost)
    }

    companion object {
        fun get(): PerinkpheralSettings =
            ApplicationManager.getApplication().getService(PerinkpheralSettings::class.java)

        /** Default location for components downloaded by the plugin. */
        fun managedDirectory(): File = File(System.getProperty("user.home"), ".perinkpheral")
    }
}
