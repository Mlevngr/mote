package com.mlevngr.inknote.plugins

import com.mlevngr.mote.plugin.api.PluginResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginResultGateTest {
    private val gate = PluginResultGate("session-1")

    @Test
    fun `accepts matching active request exactly once`() {
        gate.start("request-1", "plugin-1", "revision-1")

        assertTrue(gate.accept(result(), "revision-1"))
        assertFalse(gate.accept(result(), "revision-1"))
    }

    @Test
    fun `rejects result from stale input session`() {
        gate.start("request-1", "plugin-1", "revision-1")

        assertFalse(gate.accept(result(sessionId = "session-old"), "revision-1"))
    }

    @Test
    fun `rejects result when note changed while plugin was running`() {
        gate.start("request-1", "plugin-1", "revision-1")

        assertFalse(gate.accept(result(), "revision-2"))
    }

    @Test
    fun `rejects cancelled request`() {
        gate.start("request-1", "plugin-1", "revision-1")
        gate.cancel("request-1")

        assertFalse(gate.accept(result(), "revision-1"))
    }

    @Test
    fun `rejects disconnected plugin result`() {
        gate.start("request-1", "plugin-1", "revision-1")
        gate.disconnectPlugin("plugin-1")

        assertFalse(gate.accept(result(), "revision-1"))
    }

    @Test
    fun `invalidating session rejects all earlier requests`() {
        gate.start("request-1", "plugin-1", "revision-1")
        gate.invalidate("session-2")

        assertFalse(gate.accept(result(), "revision-1"))
    }

    private fun result(
        sessionId: String = "session-1",
        requestId: String = "request-1",
        baseRevision: String = "revision-1"
    ) = PluginResult(sessionId, requestId, baseRevision, "updated", "summary")
}
