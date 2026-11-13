// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.device

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import org.perinkpheral.clion.settings.PerinkpheralSettings
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.image.BufferedImage
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea

class DeviceToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = DevicePanel(toolWindow)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}

/** The panel's frame, scaled by a whole factor so that the pixels stay square. */
private class ScreenView : JPanel() {
    var frame: BufferedImage? = null
        set(value) {
            field = value
            repaint()
        }

    init {
        preferredSize = Dimension(FRAME_WIDTH, FRAME_HEIGHT)
        minimumSize = Dimension(FRAME_WIDTH / 2, FRAME_HEIGHT / 2)
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val image = frame ?: return
        val whole = minOf(width / image.width, height / image.height)
        val drawnWidth: Int
        val drawnHeight: Int
        if (whole >= 1) {
            drawnWidth = image.width * whole
            drawnHeight = image.height * whole
        } else {
            val scale = minOf(width.toDouble() / image.width, height.toDouble() / image.height)
            drawnWidth = (image.width * scale).toInt()
            drawnHeight = (image.height * scale).toInt()
        }
        val g = graphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            if (whole >= 1) RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            else RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(image, (width - drawnWidth) / 2, (height - drawnHeight) / 2, drawnWidth, drawnHeight, null)
    }

    companion object {
        const val FRAME_WIDTH = 400
        const val FRAME_HEIGHT = 300
    }
}

/**
 * Live screen, the four buttons, status line and log of the device.
 * The device is polled once a second while the tool window is visible.
 */
private class DevicePanel(private val toolWindow: ToolWindow) : JPanel(BorderLayout()), Disposable {
    private val screen = ScreenView()
    private val status = JBLabel("Not connected")
    private val log = JTextArea().apply {
        isEditable = false
        font = JBUI.Fonts.create("Monospaced", 12)
    }
    private val poll: ScheduledFuture<*>
    private var lastFrame = -1
    private var logEnd: Long? = null
    private var connectedHost = ""

    init {
        val buttons = JPanel(FlowLayout(FlowLayout.CENTER, 4, 4))
        for (button in DeviceButton.entries) {
            val control = JButton(button.name)
            control.toolTipText = "Click: short press. Shift+click: long press."
            control.addActionListener { event ->
                press(button, event.modifiers and ActionEvent.SHIFT_MASK != 0)
            }
            buttons.add(control)
        }
        val menu = JButton("Menu")
        menu.toolTipText = "Long press of BACK"
        menu.addActionListener { press(DeviceButton.BACK, true) }
        buttons.add(menu)
        val clear = JButton("Clear Log")
        clear.addActionListener { log.text = "" }
        buttons.add(clear)

        val top = JPanel(BorderLayout())
        screen.border = JBUI.Borders.customLine(JBColor.border())
        top.add(screen, BorderLayout.CENTER)
        val under = JPanel(BorderLayout())
        under.add(buttons, BorderLayout.NORTH)
        status.border = JBUI.Borders.empty(2, 8)
        under.add(status, BorderLayout.SOUTH)
        top.add(under, BorderLayout.SOUTH)

        val splitter = JBSplitter(true, 0.6f)
        splitter.firstComponent = top
        splitter.secondComponent = JBScrollPane(log)
        add(splitter, BorderLayout.CENTER)

        poll = AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay({ refresh() }, 0, 1, TimeUnit.SECONDS)
    }

    private fun client(): DeviceClient? {
        val host = PerinkpheralSettings.get().deviceHost
        if (host != connectedHost) {
            connectedHost = host
            lastFrame = -1
            logEnd = null
        }
        return if (host.isBlank()) null else DeviceClient(host)
    }

    private fun press(button: DeviceButton, isLong: Boolean) {
        val client = client() ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                client.press(button, isLong)
                refresh()
            } catch (error: Exception) {
                show { status.text = error.message ?: "Request failed" }
            }
        }
    }

    @Synchronized
    private fun refresh() {
        if (!toolWindow.isVisible) return
        val client = client()
        if (client == null) {
            show { status.text = "Device address is not set: Settings | Tools | perINKpheral" }
            return
        }
        try {
            val frame = client.frame()
            if (frame.number != lastFrame || screen.frame == null) {
                lastFrame = frame.number
                show { screen.frame = frame.image }
            }
            val part = client.log(logEnd)
            // A position behind the stored one means the device restarted.
            val restarted = logEnd != null && part.end < logEnd!!
            logEnd = part.end
            if (part.text.isNotEmpty() || restarted) {
                show {
                    if (restarted) log.append("--- restart ---\n")
                    log.append(part.text)
                    log.caretPosition = log.document.length
                }
            }
            val pending = if (frame.isRefreshPending) ", refresh pending" else ""
            show { status.text = "$connectedHost: frame ${frame.number}$pending" }
        } catch (error: Exception) {
            show { status.text = "$connectedHost: ${error.message ?: error.javaClass.simpleName}" }
        }
    }

    private fun show(update: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(update)
    }

    override fun dispose() {
        poll.cancel(false)
    }
}
