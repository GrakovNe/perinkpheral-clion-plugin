// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.project

import org.perinkpheral.clion.settings.PerinkpheralSettings
import java.io.File

/**
 * `~/.perinkpheral/toolchain.cmake`: the plugin's settings as CMake reads them.
 *
 * An application's CMakeLists.txt written by the plugin includes this file before
 * anything else, so CMake finds the SDK, wasi-sdk and the device without environment
 * variables. CLion does not start from a shell and has none of them.
 */
object ToolchainFile {
    const val NAME = "toolchain.cmake"

    val file: File get() = File(PerinkpheralSettings.managedDirectory(), NAME)

    /** CMake's own spelling of a path: forward slashes on every system. */
    private fun cmakePath(path: String): String = path.replace('\\', '/').replace("\"", "\\\"")

    /** Writes the file from the current settings. Returns true when its content changed. */
    fun write(): Boolean {
        val settings = PerinkpheralSettings.get()
        val sdk = cmakePath(settings.sdkPath)
        val wasi = cmakePath(settings.wasiSdkPath)
        val host = cmakePath(settings.deviceHost)
        val text = buildString {
            appendLine("# Written by the perINKpheral plugin for CLion from its settings.")
            appendLine("# Rewritten whenever they change: edit them in Settings | Tools | perINKpheral.")
            if (sdk.isNotBlank()) {
                appendLine("set(PERINKPHERAL \"$sdk\")")
                appendLine("set(ENV{PERINKPHERAL} \"$sdk\")")
            }
            if (wasi.isNotBlank()) {
                appendLine("set(ENV{WASI_SDK_PATH} \"$wasi\")")
            }
            if (host.isNotBlank()) {
                appendLine("set(ENV{PERINKPHERAL_HOST} \"$host\")")
                appendLine("set(PERINKPHERAL_HOST \"$host\" CACHE STRING \"The device's address\" FORCE)")
            }
        }
        val target = file
        if (target.isFile && target.readText() == text) {
            return false
        }
        target.parentFile.mkdirs()
        target.writeText(text)
        return true
    }
}
