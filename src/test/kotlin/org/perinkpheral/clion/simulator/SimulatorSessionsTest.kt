// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.simulator

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SimulatorSessionsTest {
    @Test
    fun secondRunCannotTakeTheTargetFromTheFirst() {
        val sessions = SimulatorSessions()
        val first = assertNotNull(sessions.claim("simulator"))
        assertNull(sessions.claim("another"))
        assertEquals("simulator", sessions.activeHost)
        first.close()
        assertNull(sessions.activeHost)
    }

    @Test
    fun oldTerminationCannotClearTheNextRun() {
        val sessions = SimulatorSessions()
        val first = assertNotNull(sessions.claim("first"))
        first.close()
        val second = assertNotNull(sessions.claim("second"))
        first.close()
        assertEquals("second", sessions.activeHost)
        second.close()
        assertNull(sessions.activeHost)
    }

    @Test
    fun concurrentLaunchesHaveOneOwner() {
        val sessions = SimulatorSessions()
        val start = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(8)
        try {
            val attempts = (1..8).map { index ->
                workers.submit<SimulatorSessions.Session?> {
                    start.await()
                    sessions.claim("simulator-$index")
                }
            }
            start.countDown()
            val owners = attempts.mapNotNull { it.get() }
            assertEquals(1, owners.size)
            assertEquals(owners.single().host, sessions.activeHost)
            owners.single().close()
            assertNull(sessions.activeHost)
        } finally {
            workers.shutdownNow()
        }
    }
}
