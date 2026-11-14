// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.project

import com.intellij.execution.RunManager
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.perinkpheral.clion.manifest.isApplicationFolder
import org.perinkpheral.clion.run.AppRunConfiguration
import org.perinkpheral.clion.run.AppRunConfigurationType
import java.io.File

/** An application folder opened as a project: CMake, the toolchain's paths and a run configuration. */
object AppProject {
    const val CMAKE_LISTS = "CMakeLists.txt"

    /** Marks a CMakeLists.txt this plugin wrote: it reads the toolchain file. */
    private const val MARK = ".perinkpheral/toolchain.cmake"

    /** The application folder the project is, or null. */
    fun folderOf(project: Project): VirtualFile? = project.guessProjectDir()?.takeIf { isApplicationFolder(it) }

    fun cmakeLists(name: String): String = """
        |# SPDX-License-Identifier: MIT
        |# A perINKpheral application as a CMake project, written by the perINKpheral plugin for CLion.
        |# Targets: $name (build), $name-run (build, upload, restart the device into it), $name-serve (live page).
        |cmake_minimum_required(VERSION 3.20)
        |
        |# The paths of the toolchain and the device's address, from the plugin's settings;
        |# without the plugin, the environment: PERINKPHERAL, WASI_SDK_PATH, PERINKPHERAL_HOST.
        |foreach(home "${'$'}ENV{HOME}" "${'$'}ENV{USERPROFILE}")
        |    if(home AND EXISTS "${'$'}{home}/$MARK")
        |        include("${'$'}{home}/$MARK")
        |        break()
        |    endif()
        |endforeach()
        |if(NOT PERINKPHERAL AND DEFINED ENV{PERINKPHERAL})
        |    set(PERINKPHERAL "${'$'}ENV{PERINKPHERAL}")
        |endif()
        |if(NOT EXISTS "${'$'}{PERINKPHERAL}/sdk/cmake/perinkpheral.cmake")
        |    message(FATAL_ERROR "The perinkpheral SDK is not found. Set it in Settings | Tools | perINKpheral, "
        |                        "or set PERINKPHERAL to a checkout of the perinkpheral repository.")
        |endif()
        |include("${'$'}{PERINKPHERAL}/sdk/cmake/perinkpheral.cmake")
        |
        |project($name C)
        |perinkpheral_app(.)
        |""".trimMargin()

    /** What the folder's CMakeLists.txt is. */
    enum class Lists { NONE, OURS, OTHER }

    fun listsOf(folder: VirtualFile): Lists {
        val file = File(folder.path, CMAKE_LISTS)
        return when {
            !file.isFile -> Lists.NONE
            file.readText().contains(MARK) -> Lists.OURS
            else -> Lists.OTHER
        }
    }

    /** Writes the CMakeLists.txt, refreshes the file system and loads or reloads the CMake project. */
    fun writeCMakeLists(project: Project, folder: VirtualFile) {
        ToolchainFile.write()
        val file = File(folder.path, CMAKE_LISTS)
        val wasThere = file.isFile
        file.writeText(cmakeLists(folder.name))
        val lists = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file) ?: return
        loadCMake(project, lists, reload = wasThere)
    }

    /** Asks CLion to load the CMake project, or to load it again. */
    fun loadCMake(project: Project, lists: VirtualFile?, reload: Boolean) {
        val id = if (reload) "CMake.ReloadCMakeProject" else "CMake.LoadCMakeProject"
        val action = ActionManager.getInstance().getAction(id) ?: return
        val context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .apply { if (lists != null) add(CommonDataKeys.VIRTUAL_FILE, lists) }
            .build()
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                ActionUtil.invokeAction(action, context, ActionPlaces.UNKNOWN, null, null)
            }
        }
    }

    /** A run configuration for the folder, created when there is none, and selected. */
    fun ensureRunConfiguration(project: Project, folder: VirtualFile) {
        val manager = RunManager.getInstance(project)
        val existing = manager.allSettings.firstOrNull {
            (it.configuration as? AppRunConfiguration)?.applicationFolder == folder.path
        }
        val settings = existing ?: manager.createConfiguration(folder.name, AppRunConfigurationType.get().factory).also {
            (it.configuration as AppRunConfiguration).applicationFolder = folder.path
            manager.addConfiguration(it)
        }
        if (manager.selectedConfiguration == null || existing == null) {
            manager.selectedConfiguration = settings
        }
    }

    /** Settings changed: the toolchain file follows them, and open application projects reload. */
    fun settingsChanged() {
        if (!ToolchainFile.write()) {
            return
        }
        for (project in ProjectManager.getInstance().openProjects) {
            val folder = folderOf(project) ?: continue
            if (listsOf(folder) == Lists.OURS) {
                val lists = folder.findChild(CMAKE_LISTS)
                loadCMake(project, lists, reload = true)
            }
        }
    }

    fun notify(project: Project, text: String, vararg actions: NotificationAction) {
        val notification = NotificationGroupManager.getInstance().getNotificationGroup("perINKpheral")
            .createNotification("perINKpheral", text, NotificationType.INFORMATION)
        actions.forEach { notification.addAction(it) }
        notification.notify(project)
    }
}

/** On opening a project: an application folder is offered what it lacks. */
class AppProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val folder = AppProject.folderOf(project) ?: return
        ToolchainFile.write()
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            AppProject.ensureRunConfiguration(project, folder)
            when (AppProject.listsOf(folder)) {
                AppProject.Lists.NONE -> AppProject.notify(
                    project, "This is a perINKpheral application without a CMakeLists.txt.",
                    NotificationAction.createSimpleExpiring("Set up for CLion") {
                        AppProject.writeCMakeLists(project, folder)
                    })
                AppProject.Lists.OTHER -> AppProject.notify(
                    project, "This CMakeLists.txt does not read the plugin's settings: " +
                        "CMake finds the SDK and wasi-sdk only through environment variables.",
                    NotificationAction.createSimpleExpiring("Replace with the plugin's") {
                        AppProject.writeCMakeLists(project, folder)
                    })
                AppProject.Lists.OURS -> Unit
            }
        }
    }
}
