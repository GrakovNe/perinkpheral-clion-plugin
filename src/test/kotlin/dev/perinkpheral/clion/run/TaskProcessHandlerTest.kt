// SPDX-License-Identifier: MIT
package dev.perinkpheral.clion.run

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import dev.perinkpheral.clion.simulator.SimulatorSessions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskProcessHandlerTest {
    @Test
    fun detachKeepsSimulatorOwnershipUntilWorkerExits() {
        val sessions = SimulatorSessions()
        val session = assertNotNull(sessions.claim("first"))
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val terminated = CountDownLatch(1)
        val handler = TaskProcessHandler {
            entered.countDown()
            check(finish.await(10, TimeUnit.SECONDS))
            0
        }
        handler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                session.close()
                terminated.countDown()
            }
        })
        handler.startNotify()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            handler.detachProcess()
            assertNull(sessions.claim("second"))
        } finally {
            finish.countDown()
        }
        assertTrue(terminated.await(5, TimeUnit.SECONDS))
        assertNotNull(sessions.claim("second")).close()
    }
}
