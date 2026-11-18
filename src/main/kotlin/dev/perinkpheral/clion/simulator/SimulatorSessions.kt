// SPDX-License-Identifier: MIT
package dev.perinkpheral.clion.simulator

/** The shared simulator port and the device panel have one owner until that run ends. */
class SimulatorSessions {
    @Volatile
    private var active: Session? = null

    val activeHost: String? get() = active?.host

    @Synchronized
    fun claim(host: String): Session? {
        if (active != null) return null
        return Session(this, host).also { active = it }
    }

    @Synchronized
    private fun release(session: Session) {
        if (active === session) active = null
    }

    class Session internal constructor(private val owner: SimulatorSessions, val host: String) : AutoCloseable {
        override fun close() = owner.release(this)
    }
}
