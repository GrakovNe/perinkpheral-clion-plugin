// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.run

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.util.concurrency.AppExecutorUtil
import org.perinkpheral.core.build.BuildError
import java.io.IOException
import java.io.OutputStream

/**
 * Work of the plugin's own shown in a Run console as a process would be: what it says is
 * the output, what it returns the exit code, and Stop cancels it. A program it starts
 * (the simulator) is its child: its output comes here and Stop ends it.
 *
 * The network is the plugin's, in the IDE's own process: programs the IDE starts may not
 * be let into the local network by the system, and the device lives there.
 */
class TaskProcessHandler(private val work: TaskProcessHandler.() -> Int) : ProcessHandler() {
    @Volatile
    var isCancelled = false
        private set

    @Volatile
    private var child: Process? = null

    override fun startNotify() {
        super.startNotify()
        AppExecutorUtil.getAppExecutorService().execute {
            val code = try {
                work()
            } catch (error: BuildError) {
                complain(error.message ?: "The build failed.")
                1
            } catch (error: IOException) {
                complain(error.message ?: error.javaClass.simpleName)
                1
            } catch (error: InterruptedException) {
                1
            } catch (error: Exception) {
                complain("${error.javaClass.simpleName}: ${error.message}")
                1
            }
            notifyProcessTerminated(if (isCancelled) 130 else code)
        }
    }

    fun say(text: String) = notifyTextAvailable(text + "\n", ProcessOutputTypes.STDOUT)

    fun complain(text: String) = notifyTextAvailable(text + "\n", ProcessOutputTypes.STDERR)

    /** Starts a program and passes its output on until it ends; returns its exit code. */
    fun runChild(command: List<String>): Int {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        child = process
        try {
            process.inputStream.bufferedReader().forEachLine { say(it) }
            return process.waitFor()
        } finally {
            child = null
        }
    }

    override fun destroyProcessImpl() {
        isCancelled = true
        // The simulator stops as a device restarts: SIGTERM, and app_on_terminate() is called.
        child?.destroy()
    }

    override fun detachProcessImpl() {
        isCancelled = true
        child?.destroy()
        notifyProcessDetached()
    }

    override fun detachIsDefault(): Boolean = false

    override fun getProcessInput(): OutputStream? = null
}
