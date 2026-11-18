// SPDX-License-Identifier: MIT
package dev.perinkpheral.clion.project

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
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import dev.perinkpheral.core.app.Applications
import dev.perinkpheral.clion.manifest.isApplicationFolder
import dev.perinkpheral.clion.settings.PerinkpheralSettings
import dev.perinkpheral.clion.run.AppRunConfiguration
import dev.perinkpheral.clion.run.AppRunConfigurationType
import dev.perinkpheral.clion.simulator.Simulator
import java.io.File

/** An application folder opened as a project: CMake, the toolchain's paths and a run configuration. */
object AppProject {
    const val CMAKE_LISTS = "CMakeLists.txt"

    /** Marks a CMakeLists.txt this plugin wrote: it reads the plugin's settings. */
    private const val MARK = ".perinkpheral/toolchain.cmake"

    /** The application folder the project is, or null. */
    fun folderOf(project: Project): VirtualFile? = project.guessProjectDir()?.takeIf { isApplicationFolder(it) }

    fun cmakeLists(name: String): String = """
        |# SPDX-License-Identifier: MIT
        |# A perINKpheral application as a CMake project, written by the perINKpheral plugin for CLion.
        |# CMake reads and compiles the code; the plugin builds the package, checks it and runs it
        |# on the device or in the simulator.
        |cmake_minimum_required(VERSION 3.20)
        |
        |# The SDK, wasi-sdk and the device, from the plugin's settings; without the plugin, the
        |# environment: PERINKPHERAL, WASI_SDK_PATH, PERINKPHERAL_HOST.
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
        writeHeaders(project, File(folder.path))
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

    /** Run configurations for the folder - on the device, and in the simulator - created when
     * there are none; the device's is selected. */
    fun ensureRunConfiguration(project: Project, folder: VirtualFile) {
        val manager = RunManager.getInstance(project)
        fun find(inSimulator: Boolean) = manager.allSettings.firstOrNull {
            val configuration = it.configuration as? AppRunConfiguration
            configuration?.applicationFolder == folder.path && configuration.inSimulator == inSimulator
        }
        fun create(inSimulator: Boolean) = find(inSimulator) ?: manager.createConfiguration(
            if (inSimulator) "${folder.name} (simulator)" else folder.name,
            AppRunConfigurationType.get().factory
        ).also {
            val configuration = it.configuration as AppRunConfiguration
            configuration.applicationFolder = folder.path
            configuration.inSimulator = inSimulator
            manager.addConfiguration(it)
        }
        val hadDevice = find(false) != null
        val onDevice = create(false)
        create(true)
        if (manager.selectedConfiguration == null || !hadDevice) {
            manager.selectedConfiguration = onDevice
        }
    }

    /** The problem with an application's strings or pictures said last, by its folder. */
    private val problemsSaid = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** strings.h and pictures.h of the application, for the code to read. A problem with its
     * strings or pictures is said once: not again for every file saved while it lasts. */
    fun writeHeaders(project: Project?, appDir: File) {
        val problem = ToolchainFile.writeHeaders(appDir)
        if (problem == null) {
            problemsSaid.remove(appDir.path)
            return
        }
        if (problemsSaid.put(appDir.path, problem) != problem && project != null) notify(project, problem)
    }

    /** A new application in `folder`: the SDK's sample under its name, with the plugin's
     * CMakeLists.txt. Returns its source file. */
    fun create(folder: File, title: String, author: String): File =
        Applications.create(File(PerinkpheralSettings.get().sdkPath), folder, title, author, ::cmakeLists)

    /** Settings changed: the toolchain file follows them, and open application projects reload. */
    fun settingsChanged() {
        if (!ToolchainFile.write()) {
            return
        }
        for (project in ProjectManager.getInstance().openProjects) {
            val folder = folderOf(project) ?: continue
            writeHeaders(project, File(folder.path))
            if (listsOf(folder) == Lists.OURS) {
                val lists = folder.findChild(CMAKE_LISTS)
                loadCMake(project, lists, reload = true)
            }
        }
    }

    /** The headers are made again when a file of `strings` or `pictures` changes. */
    fun watchStringsAndPictures(project: Project, appDir: File) {
        val watched = listOf(File(appDir, "strings").path, File(appDir, "pictures").path)
        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { event -> watched.any { event.path.startsWith(it) } }) {
                    ApplicationManager.getApplication().executeOnPooledThread { writeHeaders(project, appDir) }
                }
            }
        })
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
        // A toolchain file written by an older plugin, without what this one puts in it: CMake,
        // which may have read it already, reads it again.
        if (ToolchainFile.write() && AppProject.listsOf(folder) == AppProject.Lists.OURS) {
            AppProject.loadCMake(project, folder.findChild(AppProject.CMAKE_LISTS), reload = true)
        }
        AppProject.writeHeaders(project, File(folder.path))
        AppProject.watchStringsAndPictures(project, File(folder.path))
        if (!Simulator.hasGlyphs()) {
            // Letters beyond ASCII for the simulator, quietly, once.
            ApplicationManager.getApplication().executeOnPooledThread { Simulator.fetchGlyphs(null) }
        }
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
                    project, "This CMakeLists.txt does not read the plugin's settings: CMake finds the SDK and " +
                        "wasi-sdk only through environment variables.",
                    NotificationAction.createSimpleExpiring("Replace with the plugin's") {
                        AppProject.writeCMakeLists(project, folder)
                    })
                AppProject.Lists.OURS -> Unit
            }
        }
    }
}
