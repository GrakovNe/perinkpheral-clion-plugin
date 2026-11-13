// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.toolchain

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.HttpRequests
import org.perinkpheral.clion.settings.PerinkpheralSettings
import java.io.File
import java.io.IOException

class SetUpToolchainAction : AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        SetUpToolchainTask(event.project).queue()
    }
}

/**
 * Downloads what the settings do not point to:
 * the SDK checkout (git clone) and wasi-sdk (release archive for this platform).
 * Components are placed in `~/.perinkpheral`. Existing valid settings are kept.
 */
class SetUpToolchainTask(project: Project?) : Task.Backgroundable(project, "Setting up perINKpheral toolchain", true) {
    private val done = mutableListOf<String>()

    override fun run(indicator: ProgressIndicator) {
        val settings = PerinkpheralSettings.get()
        val home = PerinkpheralSettings.managedDirectory()
        home.mkdirs()
        if (!settings.isSdkValid) {
            settings.state.sdkPath = Toolchain.fetchSdk(home, indicator).path
            done += "SDK"
        }
        if (!settings.isWasiSdkValid) {
            settings.state.wasiSdkPath = Toolchain.fetchWasiSdk(home, indicator).path
            done += "wasi-sdk"
        }
        Toolchain.run(null, indicator, settings.pythonPath, "--version")
    }

    override fun onSuccess() {
        val text = if (done.isEmpty()) "Toolchain is ready." else "Installed: ${done.joinToString(", ")}."
        Tools.notify(project, text)
    }

    override fun onThrowable(error: Throwable) {
        Tools.notify(project, "Toolchain setup failed: ${error.message}", NotificationType.ERROR)
    }
}

object Toolchain {
    private const val SDK_REPOSITORY = "https://github.com/GrakovNe/perinkpheral.git"
    private const val WASI_SDK_VERSION = "34"

    /** Runs a command to its end; throws with the output when it fails. */
    fun run(directory: File?, indicator: ProgressIndicator?, vararg command: String): String {
        val process = try {
            ProcessBuilder(*command).directory(directory).redirectErrorStream(true).start()
        } catch (error: IOException) {
            throw IOException("${command[0]} is not installed or not on PATH")
        }
        val output = StringBuilder()
        process.inputStream.bufferedReader().forEachLine { line ->
            output.appendLine(line)
            indicator?.text2 = line.take(120)
            if (indicator?.isCanceled == true) process.destroy()
        }
        if (process.waitFor() != 0) {
            throw IOException("${command.joinToString(" ")}: ${output.toString().trim().takeLast(400)}")
        }
        return output.toString()
    }

    fun fetchSdk(home: File, indicator: ProgressIndicator): File {
        val target = File(home, "sdk")
        indicator.text = "Cloning the SDK"
        if (File(target, ".git").isDirectory) {
            run(target, indicator, "git", "pull", "--ff-only")
        } else {
            run(home, indicator, "git", "clone", "--depth", "1", SDK_REPOSITORY, target.path)
        }
        return target
    }

    private fun wasiSdkPlatform(): String {
        val cpu = if (SystemInfo.isAarch64) "arm64" else "x86_64"
        val system = when {
            SystemInfo.isMac -> "macos"
            SystemInfo.isWindows -> "windows"
            else -> "linux"
        }
        return "$cpu-$system"
    }

    fun fetchWasiSdk(home: File, indicator: ProgressIndicator): File {
        val name = "wasi-sdk-$WASI_SDK_VERSION.0-${wasiSdkPlatform()}"
        val target = File(home, name)
        if (File(target, "bin/clang").isFile || File(target, "bin/clang.exe").isFile) return target
        val archive = File(home, "$name.tar.gz")
        indicator.text = "Downloading $name"
        HttpRequests.request(
            "https://github.com/WebAssembly/wasi-sdk/releases/download/wasi-sdk-$WASI_SDK_VERSION/$name.tar.gz"
        ).saveToFile(archive, indicator)
        indicator.text = "Unpacking $name"
        indicator.isIndeterminate = true
        run(home, indicator, "tar", "-xzf", archive.path)
        archive.delete()
        if (!target.isDirectory) throw IOException("$name was not found in the archive")
        return target
    }

    /**
     * A Python environment with esptool, made on first use in `~/.perinkpheral/venv`.
     * Returns its interpreter.
     */
    fun fetchEsptool(indicator: ProgressIndicator): File {
        val home = PerinkpheralSettings.managedDirectory()
        val environment = File(home, "venv")
        val python = File(environment, if (SystemInfo.isWindows) "Scripts/python.exe" else "bin/python")
        if (!python.isFile) {
            indicator.text = "Creating a Python environment"
            home.mkdirs()
            run(home, indicator, PerinkpheralSettings.get().pythonPath, "-m", "venv", environment.path)
        }
        val isInstalled = try {
            run(home, null, python.path, "-m", "esptool", "version")
            true
        } catch (error: IOException) {
            false
        }
        if (!isInstalled) {
            indicator.text = "Installing esptool"
            run(home, indicator, python.path, "-m", "pip", "install", "--disable-pip-version-check", "esptool>=5,<6")
        }
        return python
    }
}
