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
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.perinkpheral.clion.build.Json
import org.perinkpheral.clion.manifest.isApplicationFolder
import org.perinkpheral.clion.settings.PerinkpheralSettings
import org.perinkpheral.clion.run.AppRunConfiguration
import org.perinkpheral.clion.run.AppRunConfigurationType
import org.perinkpheral.clion.simulator.Simulator
import java.io.File

/** An application folder opened as a project: CMake, the toolchain's paths and a run configuration. */
object AppProject {
    const val CMAKE_LISTS = "CMakeLists.txt"

    /** Marks a CMakeLists.txt this plugin wrote: it reads the plugin's app.cmake. */
    private const val MARK = ".perinkpheral/app.cmake"

    /** The application folder the project is, or null. */
    fun folderOf(project: Project): VirtualFile? = project.guessProjectDir()?.takeIf { isApplicationFolder(it) }

    fun cmakeLists(name: String): String = """
        |# SPDX-License-Identifier: MIT
        |# A perINKpheral application as a CMake project, written by the perINKpheral plugin for CLion.
        |# CMake reads and compiles the code; the plugin builds the package, checks it and runs it
        |# on the device or in the simulator.
        |cmake_minimum_required(VERSION 3.20)
        |
        |# The SDK and wasi-sdk, from the plugin's settings.
        |foreach(home "${'$'}ENV{HOME}" "${'$'}ENV{USERPROFILE}")
        |    if(home AND EXISTS "${'$'}{home}/$MARK")
        |        include("${'$'}{home}/$MARK")
        |        break()
        |    endif()
        |endforeach()
        |if(NOT COMMAND perinkpheral_app)
        |    message(FATAL_ERROR "Open this application in CLion with the perINKpheral plugin: "
        |                        "it writes ~/$MARK from Settings | Tools | perINKpheral.")
        |endif()
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

    /** strings.h and pictures.h of the application, for the code to read; a problem with its
     * strings or pictures is said once. */
    fun writeHeaders(project: Project?, appDir: File) {
        val problem = ToolchainFile.writeHeaders(appDir) ?: return
        if (project != null) notify(project, problem)
    }

    /** A new application in `folder`: the SDK's sample under its name, a manifest of its own,
     * and a CMakeLists.txt. Returns its source file. */
    fun create(folder: File, title: String): File {
        val sample = File(PerinkpheralSettings.get().sdkPath, "sdk/apps/hello")
        val name = folder.name
        @Suppress("UNCHECKED_CAST")
        val manifest = LinkedHashMap(Json.parse(File(sample, "manifest.json").readText()) as Map<String, Any?>)
        manifest["name"] = name
        manifest["title"] = title.ifBlank { name.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() } }
        manifest["version"] = "0.1.0" // a new application's, not the sample's
        folder.mkdirs()
        File(folder, "manifest.json").writeText(Json.pretty(manifest) + "\n")
        // The sample's code, without the line that says whose it is: the new one is its author's.
        val source = File(folder, "$name.c")
        source.writeText(File(sample, "hello.c").readLines().filterNot { it.startsWith(" * Copyright") }
            .joinToString("\n", postfix = "\n"))
        File(folder, CMAKE_LISTS).writeText(cmakeLists(name))
        return source
    }

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
        ToolchainFile.write()
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
                    project, "This CMakeLists.txt is not the plugin's: CMake may not find the SDK, " +
                        "wasi-sdk and the headers made of the strings and pictures.",
                    NotificationAction.createSimpleExpiring("Replace with the plugin's") {
                        AppProject.writeCMakeLists(project, folder)
                    })
                AppProject.Lists.OURS -> Unit
            }
        }
    }
}
