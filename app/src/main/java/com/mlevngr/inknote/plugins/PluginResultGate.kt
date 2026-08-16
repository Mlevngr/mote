package com.mlevngr.inknote.plugins

import com.mlevngr.mote.plugin.api.PluginResult

class PluginResultGate(private var sessionId: String) {
    private val active = mutableMapOf<String, ActiveRequest>()

    fun start(requestId: String, pluginId: String, baseRevision: String) {
        active[requestId] = ActiveRequest(pluginId, baseRevision)
    }

    fun disconnectPlugin(pluginId: String) {
        active.entries.removeAll { it.value.pluginId == pluginId }
    }

    fun cancel(requestId: String) {
        active.remove(requestId)
    }

    fun accept(result: PluginResult, currentRevision: String): Boolean {
        if (result.sessionId != sessionId) return false
        val request = active[result.requestId] ?: return false
        if (request.baseRevision != result.baseRevision) return false
        if (currentRevision != result.baseRevision) return false
        active.remove(result.requestId)
        return true
    }

    fun invalidate(newSessionId: String) {
        active.clear()
        sessionId = newSessionId
    }

    private data class ActiveRequest(
        val pluginId: String,
        val baseRevision: String
    )
}
