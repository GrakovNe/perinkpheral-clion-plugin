// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.run

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import org.perinkpheral.clion.project.AppProject
import org.perinkpheral.clion.toolchain.Tools
import java.io.File

/** Creates an application folder from the SDK sample. */
class NewApplicationAction : AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        if (!Tools.requireToolchain(project, needsDevice = false)) return
        val selected = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val parent = (if (selected != null && selected.isDirectory) selected else project.guessProjectDir())
            ?: return
        val name = Messages.showInputDialog(project, "Name (1-13 of a-z, 0-9, '-', '_'):", "New Application",
            null, "", object : InputValidator {
                override fun checkInput(input: String): Boolean =
                    Regex("[a-z0-9_-]{1,13}").matches(input) && !File(parent.path, input).exists()
                override fun canClose(input: String): Boolean = checkInput(input)
            }) ?: return
        val folder = File(parent.path, name)
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val created = AppProject.create(folder, "")
                val source = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(created)
                parent.refresh(true, true)
                if (source != null) {
                    ApplicationManager.getApplication().invokeLater {
                        FileEditorManager.getInstance(project).openFile(source, true)
                    }
                }
            } catch (error: Exception) {
                Tools.notify(project, error.message ?: "Application was not created.", NotificationType.ERROR)
            }
        }
    }
}
