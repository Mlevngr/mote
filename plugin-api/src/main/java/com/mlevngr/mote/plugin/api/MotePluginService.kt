package com.mlevngr.mote.plugin.api

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Binder

abstract class MotePluginService : Service() {
    protected abstract val pluginDescriptor: PluginDescriptor

    protected abstract fun execute(
        request: PluginRequest,
        callback: IMotePluginCallback
    )

    protected open fun cancel(requestId: String) = Unit

    protected abstract fun isCallerAllowed(uid: Int): Boolean

    private val binder = object : IMotePlugin.Stub() {
        override fun getDescriptor() = withAllowedCaller {
            PluginBundles.descriptor(pluginDescriptor)
        }

        override fun execute(requestBundle: android.os.Bundle, callback: IMotePluginCallback) {
            requireAllowedCaller()
            val request = PluginBundles.parseRequest(requestBundle)
            if (request == null) {
                callback.onError(
                    PluginBundles.error(
                        PluginError(
                            sessionId = requestBundle.getString(PluginContract.KEY_SESSION_ID).orEmpty(),
                            requestId = requestBundle.getString(PluginContract.KEY_REQUEST_ID).orEmpty(),
                            code = PluginContract.ERROR_INVALID_REQUEST,
                            message = "Invalid plugin request"
                        )
                    )
                )
                return
            }
            runCatching { execute(request, callback) }.onFailure { error ->
                callback.onError(
                    PluginBundles.error(
                        PluginError(
                            request.sessionId,
                            request.requestId,
                            PluginContract.ERROR_PLUGIN_FAILURE,
                            error.message ?: "Plugin execution failed"
                        )
                    )
                )
            }
        }

        override fun cancel(requestId: String) {
            requireAllowedCaller()
            runCatching { cancel(requestId) }
        }
    }

    final override fun onBind(intent: Intent?): IBinder? =
        binder.takeIf { intent?.action == PluginContract.SERVICE_ACTION }

    private fun requireAllowedCaller() {
        if (!isCallerAllowed(Binder.getCallingUid())) {
            throw SecurityException("Plugin caller is not allowed")
        }
    }

    private inline fun <T> withAllowedCaller(block: () -> T): T {
        requireAllowedCaller()
        return block()
    }
}
