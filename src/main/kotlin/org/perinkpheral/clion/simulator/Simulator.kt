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
 * On macOS the binary is in the plugin (one for arm64 and x86_64) and is put in
 * `~/.perinkpheral/simulator/bin`. Elsewhere `devtool.py sim` builds it from the SDK
 * checkout, which needs a C compiler and libcurl.
 */
object Simulator {
    const val PORT = 8401
    const val HOST = "127.0.0.1:$PORT"
    private const val RESOURCE = "/simulator/macos/perinkpheral-sim"
    private const val GLYPHS_URL = "https://raw.githubusercontent.com/GrakovNe/perinkpheral-apps/HEAD/catalog/glyphs/"
    private val GLYPH_PACKS = listOf("latin", "cyrillic", "greek")

    /** The host the device window and the tools talk to while a simulator runs; null otherwise. */
    @Volatile
    var activeHost: String? = null

    private fun home(): File = File(PerinkpheralSettings.managedDirectory(), "simulator")

    /** The binary for this computer, or null when `devtool.py sim` is to build one. */
    fun binary(): File? {
        if (!SystemInfo.isMac) return null
        val stream = Simulator::class.java.getResourceAsStream(RESOURCE) ?: return null
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
