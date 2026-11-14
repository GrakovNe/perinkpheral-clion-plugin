// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import org.perinkpheral.clion.project.AppProject
import javax.swing.JComponent
import javax.swing.JPanel

/** Settings | Tools | perINKpheral. */
class PerinkpheralConfigurable : Configurable {
    private val sdkPath = TextFieldWithBrowseButton()
    private val wasiSdkPath = TextFieldWithBrowseButton()
    private val pythonPath = JBTextField()
    private val deviceHost = JBTextField()
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "perINKpheral"

    override fun createComponent(): JComponent {
        fun folder(title: String) = FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle(title)
        sdkPath.addBrowseFolderListener(null, folder("perinkpheral Checkout"))
        wasiSdkPath.addBrowseFolderListener(null, folder("wasi-sdk"))
        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("SDK checkout:"), sdkPath, 1, false)
            .addTooltip("The perinkpheral repository: contains sdk/ and tools/.")
            .addLabeledComponent(JBLabel("wasi-sdk:"), wasiSdkPath, 1, false)
            .addTooltip("An unpacked wasi-sdk: contains bin/clang.")
            .addLabeledComponent(JBLabel("Python 3:"), pythonPath, 1, false)
            .addLabeledComponent(JBLabel("Device address:"), deviceHost, 1, false)
            .addTooltip("Shown on the second page of About on the device.")
            .addComponentFillVertically(JPanel(), 0)
            .panel
        panel = form
        return form
    }

    private fun stored() = PerinkpheralSettings.get().state

    override fun isModified(): Boolean {
        val state = stored()
        return sdkPath.text != state.sdkPath || wasiSdkPath.text != state.wasiSdkPath ||
            pythonPath.text != state.pythonPath || deviceHost.text != state.deviceHost
    }

    override fun apply() {
        val state = stored()
        state.sdkPath = sdkPath.text.trim()
        state.wasiSdkPath = wasiSdkPath.text.trim()
        state.pythonPath = pythonPath.text.trim()
        state.deviceHost = deviceHost.text.trim()
        AppProject.settingsChanged()
    }

    override fun reset() {
        val state = stored()
        sdkPath.text = state.sdkPath
        wasiSdkPath.text = state.wasiSdkPath
        pythonPath.text = state.pythonPath
        deviceHost.text = state.deviceHost
    }

    override fun disposeUIResources() {
        panel = null
    }
}
