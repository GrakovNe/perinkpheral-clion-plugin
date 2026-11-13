// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.LocatableRunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.util.ui.FormBuilder
import org.perinkpheral.clion.manifest.findApplicationFolder
import org.perinkpheral.clion.settings.PerinkpheralSettings
import org.perinkpheral.clion.toolchain.SourceLinkFilter
import org.perinkpheral.clion.toolchain.Tools
import java.io.File
import javax.swing.JComponent

private val ICON = IconLoader.getIcon("/META-INF/pluginIcon.svg", AppRunConfigurationType::class.java)

/** Run configuration type "perINKpheral application". */
class AppRunConfigurationType : ConfigurationTypeBase(
    "PerinkpheralApplication", "perINKpheral Application",
    "Build an application, upload it and restart the device into it", ICON
) {
    val factory: ConfigurationFactory = object : ConfigurationFactory(this) {
        override fun getId(): String = "PerinkpheralApplication"
        override fun createTemplateConfiguration(project: Project): RunConfiguration =
            AppRunConfiguration(project, this, "")
        override fun getOptionsClass(): Class<AppRunOptions> = AppRunOptions::class.java
    }

    init {
        addFactory(factory)
    }

    companion object {
        fun get(): AppRunConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(AppRunConfigurationType::class.java)
    }
}

class AppRunOptions : LocatableRunConfigurationOptions() {
    private val storedFolder = string("").provideDelegate(this, "applicationFolder")
    var applicationFolder: String
        get() = storedFolder.getValue(this) ?: ""
        set(value) = storedFolder.setValue(this, value)
}

/** Runs `devtool.py run <application folder>`: build, upload, restart into the application. */
class AppRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    LocatableConfigurationBase<AppRunOptions>(project, factory, name) {

    override fun getOptions(): AppRunOptions = super.getOptions() as AppRunOptions

    var applicationFolder: String
        get() = options.applicationFolder
        set(value) {
            options.applicationFolder = value
        }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = AppRunSettingsEditor(project)

    override fun checkConfiguration() {
        if (!File(applicationFolder, "manifest.json").isFile) {
            throw RuntimeConfigurationError("Application folder has no manifest.json.")
        }
        val settings = PerinkpheralSettings.get()
        if (!settings.isSdkValid) throw RuntimeConfigurationError("SDK checkout is not set.")
        if (!settings.isWasiSdkValid) throw RuntimeConfigurationError("wasi-sdk is not set.")
        if (settings.deviceHost.isBlank()) throw RuntimeConfigurationError("Device address is not set.")
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        val folder = File(applicationFolder)
        return object : CommandLineState(environment) {
            init {
                addConsoleFilters(SourceLinkFilter(project, folder))
            }

            override fun startProcess(): ProcessHandler {
                if (!Tools.requireToolchain(project, needsDevice = true)) {
                    throw ExecutionException("Toolchain is not set up.")
                }
                val commandLine = Tools.python("tools/devtool/devtool.py", "run", folder.path)
                    .withWorkDirectory(folder)
                val handler = OSProcessHandler(commandLine)
                ProcessTerminatedListener.attach(handler)
                return handler
            }
        }
    }
}

private class AppRunSettingsEditor(private val project: Project) : SettingsEditor<AppRunConfiguration>() {
    private val folder = TextFieldWithBrowseButton()

    override fun createEditor(): JComponent {
        folder.addBrowseFolderListener(project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle("Application Folder"))
        return FormBuilder.createFormBuilder().addLabeledComponent("Application folder:", folder).panel
    }

    override fun resetEditorFrom(configuration: AppRunConfiguration) {
        folder.text = configuration.applicationFolder
    }

    override fun applyEditorTo(configuration: AppRunConfiguration) {
        configuration.applicationFolder = folder.text.trim()
    }
}

/** Offers the configuration for a file or folder inside an application folder. */
class AppRunConfigurationProducer : LazyRunConfigurationProducer<AppRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory = AppRunConfigurationType.get().factory

    private fun folderOf(context: ConfigurationContext): String? =
        findApplicationFolder(context.location?.virtualFile)?.path

    override fun setupConfigurationFromContext(configuration: AppRunConfiguration, context: ConfigurationContext,
                                               sourceElement: Ref<PsiElement>): Boolean {
        val folder = folderOf(context) ?: return false
        configuration.applicationFolder = folder
        configuration.name = File(folder).name
        return true
    }

    override fun isConfigurationFromContext(configuration: AppRunConfiguration, context: ConfigurationContext): Boolean =
        folderOf(context) == configuration.applicationFolder
}
