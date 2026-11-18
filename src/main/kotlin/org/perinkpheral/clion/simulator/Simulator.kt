// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.simulator

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.HttpRequests
import org.perinkpheral.clion.settings.PerinkpheralSettings
import java.io.File
import java.io.IOException

/**
 * The simulator the plugin carries, and what it needs from outside.
 *
 * The plugin carries the binary for macOS (one for arm64 and x86_64) and for Linux
 * on x86_64 and aarch64 (static, any distribution); the one for this computer is put
 * in `~/.perinkpheral/simulator/bin`. Its network is the plugin's own (the core's SimulatorNetwork).
 * Windows is not supported yet.
 */
object Simulator {
    const val PORT = 8401
    const val HOST = "127.0.0.1:$PORT"
    private const val GLYPHS_URL = "https://raw.githubusercontent.com/GrakovNe/perinkpheral-apps/HEAD/catalog/glyphs/"
    private val GLYPH_PACKS = listOf("latin", "cyrillic", "greek")

    /** The host the device window and the tools talk to while a simulator runs; null otherwise. */
    val sessions = SimulatorSessions()
    val activeHost: String? get() = sessions.activeHost

    /** Where the simulator keeps its binary, its glyph packs and a state an application. */
    fun home(): File = File(PerinkpheralSettings.managedDirectory(), "simulator")

    /** The folder of the plugin's binary for this computer, or null when there is none. */
    fun platform(): String? {
        val arch = System.getProperty("os.arch").lowercase()
        return when {
            SystemInfo.isMac -> "macos"
            SystemInfo.isLinux && (arch == "amd64" || arch == "x86_64") -> "linux-x86_64"
            SystemInfo.isLinux && (arch == "aarch64" || arch == "arm64") -> "linux-aarch64"
            else -> null
        }
    }

    /** The binary for this computer, or null on a system it is not built for. */
    fun binary(): File? {
        val platform = platform() ?: return null
        val stream = Simulator::class.java.getResourceAsStream("/simulator/$platform/perinkpheral-sim") ?: return null
        val bytes = stream.use { it.readBytes() }
        val target = File(home(), "bin/perinkpheral-sim")
        if (!target.isFile || target.length() != bytes.size.toLong() || !target.readBytes().contentEquals(bytes)) {
            target.parentFile.mkdirs()
            target.writeBytes(bytes)
        }
        target.setExecutable(true)
        return target
    }

    /** Where the glyph packs are kept; possibly empty. */
    fun glyphsFolder(): File = File(home(), "glyphs")

    fun hasGlyphs(): Boolean = GLYPH_PACKS.all { File(glyphsFolder(), "$it.glyphs").isFile }

    /** The glyph packs of the official catalog, fetched once: letters beyond ASCII. */
    fun fetchGlyphs(indicator: ProgressIndicator?): File {
        val folder = glyphsFolder()
        folder.mkdirs()
        for (pack in GLYPH_PACKS) {
            val file = File(folder, "$pack.glyphs")
            if (file.isFile) continue
            indicator?.text = "Downloading the $pack glyph pack"
            try {
                HttpRequests.request(GLYPHS_URL + "$pack.glyphs").saveToFile(file, indicator)
            } catch (error: IOException) {
                file.delete()
            }
        }
        return folder
    }
}
