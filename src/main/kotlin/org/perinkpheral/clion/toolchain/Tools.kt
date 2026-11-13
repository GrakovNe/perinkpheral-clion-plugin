// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.toolchain

import com.intellij.execution.RunContentExecutor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import org.perinkpheral.clion.settings.PerinkpheralConfigurable
import org.perinkpheral.clion.settings.PerinkpheralSettings
import java.io.File
import java.nio.charset.StandardCharsets

object Tools {
    fun notify(project: Project?, text: String, type: NotificationType = NotificationType.INFORMATION) {
        NotificationGroupManager.getInstance().getNotificationGroup("perINKpheral")
            .createNotification("perINKpheral", text, type).notify(project)
    }

    /** A command line of an SDK tool: `python <sdk>/<script> <arguments>`, with the tool environment. */
    fun python(script: String, vararg arguments: String): GeneralCommandLine {
        val settings = PerinkpheralSettings.get()
        return GeneralCommandLine(settings.pythonPath, settings.tool(script).path, *arguments)
            .withEnvironment(settings.toolEnvironment())
            .withCharset(StandardCharsets.UTF_8)
    }

    /** Runs a command line in the Run tool window. `afterExit` receives the exit code. */
    fun runInConsole(project: Project, title: String, commandLine: GeneralCommandLine,
                     afterExit: ((Int) -> Unit)? = null) {
        val handler = OSProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        if (afterExit != null) {
            handler.addProcessListener(object : com.intellij.execution.process.ProcessListener {
                override fun processTerminated(event: com.intellij.execution.process.ProcessEvent) {
                    afterExit(event.exitCode)
                }
            })
        }
        RunContentExecutor(project, handler)
            .withTitle(title)
            .withFilter(SourceLinkFilter(project, commandLine.workDirectory))
            .withActivateToolWindow(true)
            .run()
    }

    /**
     * Reports what the toolchain lacks and offers the setup action.
     * Returns true when the SDK tools can be run.
     */
    fun requireToolchain(project: Project?, needsDevice: Boolean): Boolean {
        val settings = PerinkpheralSettings.get()
        val missing = when {
            !settings.isSdkValid -> "SDK checkout is not set."
            !settings.isWasiSdkValid -> "wasi-sdk is not set."
            needsDevice && settings.deviceHost.isBlank() -> "Device address is not set."
            else -> return true
        }
        val notification = NotificationGroupManager.getInstance().getNotificationGroup("perINKpheral")
            .createNotification("perINKpheral", missing, NotificationType.WARNING)
        if (!settings.isSdkValid || !settings.isWasiSdkValid) {
            notification.addAction(com.intellij.notification.NotificationAction.createSimpleExpiring("Set up toolchain") {
                SetUpToolchainTask(project).queue()
            })
        }
        notification.addAction(com.intellij.notification.NotificationAction.createSimple("Settings") {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, PerinkpheralConfigurable::class.java)
        })
        notification.notify(project)
        return false
    }
}

/** Turns `file:line[:column]` of compiler and tool output into links. */
class SourceLinkFilter(private val project: Project, private val base: File?) : Filter {
    private val location = Regex("""([A-Za-z]:)?([^\s:"']+\.(?:c|h|json)):(\d+)(?::(\d+))?""")

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val match = location.find(line) ?: return null
        val path = (match.groupValues[1] + match.groupValues[2])
        val file = File(path).let { if (it.isAbsolute || base == null) it else File(base, path) }
        val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file) ?: return null
        val row = match.groupValues[3].toInt() - 1
        val column = match.groupValues[4].toIntOrNull()?.minus(1) ?: 0
        val start = entireLength - line.length + match.range.first
        val end = entireLength - line.length + match.range.last + 1
        return Filter.Result(start, end, OpenFileHyperlinkInfo(project, virtualFile, row, column))
    }
}
