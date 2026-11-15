// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
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
    private val deviceHost = JBTextField()
    private val simulatorLanguage = JBTextField()
    private val simulatorFont = ComboBox(arrayOf("departure", "jersey"))
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "perINKpheral"

    override fun createComponent(): JComponent {
        fun folder(title: String) = FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle(title)
        sdkPath.addBrowseFolderListener(null, folder("perinkpheral Checkout"))
        wasiSdkPath.addBrowseFolderListener(null, folder("wasi-sdk"))
        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("SDK checkout:"), sdkPath, 1, false)
            .addTooltip("The perinkpheral repository: contains sdk/.")
            .addLabeledComponent(JBLabel("wasi-sdk:"), wasiSdkPath, 1, false)
            .addTooltip("An unpacked wasi-sdk: contains bin/clang.")
            .addLabeledComponent(JBLabel("Device address:"), deviceHost, 1, false)
            .addTooltip("Shown on the second page of About on the device.")
            .addLabeledComponent(JBLabel("Simulator language:"), simulatorLanguage, 1, false)
            .addTooltip("A code of sdk/languages.json: en, ru, de ...")
            .addLabeledComponent(JBLabel("Simulator font:"), simulatorFont, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        panel = form
        return form
    }

    private fun stored() = PerinkpheralSettings.get().state

    override fun isModified(): Boolean {
        val state = stored()
        return sdkPath.text != state.sdkPath || wasiSdkPath.text != state.wasiSdkPath ||
            deviceHost.text != state.deviceHost ||
            simulatorLanguage.text != state.simulatorLanguage || simulatorFont.selectedItem != state.simulatorFont
    }

    override fun apply() {
        val state = stored()
        state.sdkPath = sdkPath.text.trim()
        state.wasiSdkPath = wasiSdkPath.text.trim()
        state.deviceHost = deviceHost.text.trim()
        state.simulatorLanguage = simulatorLanguage.text.trim()
        state.simulatorFont = simulatorFont.selectedItem as String
        AppProject.settingsChanged()
    }

    override fun reset() {
        val state = stored()
        sdkPath.text = state.sdkPath
        wasiSdkPath.text = state.wasiSdkPath
        deviceHost.text = state.deviceHost
        simulatorLanguage.text = state.simulatorLanguage
        simulatorFont.selectedItem = state.simulatorFont
    }

    override fun disposeUIResources() {
        panel = null
    }
}
