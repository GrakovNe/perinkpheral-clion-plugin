// SPDX-License-Identifier: MIT
package dev.perinkpheral.clion.run

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import dev.perinkpheral.clion.manifest.findApplicationFolder
import dev.perinkpheral.clion.toolchain.Tools
import java.io.File

/**
 * Builds the application, installs it on the device and restarts the device into it.
 * The application is the one the selected file belongs to, or the project.
 */
class UploadToDeviceAction : AnAction(), DumbAware {
    private fun folderOf(event: AnActionEvent): VirtualFile? =
        findApplicationFolder(event.getData(CommonDataKeys.VIRTUAL_FILE))
            ?: findApplicationFolder(event.project?.guessProjectDir())

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val folder = folderOf(event)
        event.presentation.isEnabledAndVisible = event.project != null && folder != null
        if (folder != null) {
            event.presentation.text = "Upload ${folder.name} to Device"
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val folder = folderOf(event) ?: return
        if (!Tools.requireToolchain(project, needsDevice = true)) return
        FileDocumentManager.getInstance().saveAllDocuments()
        val directory = File(folder.path)
        Tools.runInConsole(project, "Upload ${folder.name}", TaskProcessHandler { AppRuns.onDevice(this, directory) },
            directory)
    }
}
