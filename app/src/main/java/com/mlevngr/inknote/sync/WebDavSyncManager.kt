package com.mlevngr.inknote.sync

import android.content.Context
import com.mlevngr.inknote.library.NoteLibrary
import java.io.File

class WebDavSyncManager(context: Context) {
    private val root = NoteLibrary(context).storageRoot
    private val state = WebDavSyncState(File(context.filesDir, "webdav-sync-state.properties"))

    fun testConnections(config: WebDavConfig): List<WebDavConnectionResult> {
        val validated = config.validated()
        return validated.endpoints().map { endpoint ->
            runCatching { WebDavTransport(validated, endpoint).testConnection() }
                .fold(
                    onSuccess = { WebDavConnectionResult(endpoint, true, "连接成功") },
                    onFailure = { WebDavConnectionResult(endpoint, false, safeMessage(it)) }
                )
        }
    }

    fun sync(config: WebDavConfig): WebDavSyncReport {
        val validated = config.validated()
        return WebDavEndpointFallback.run(validated.endpoints()) { endpoint ->
                val transport = WebDavTransport(validated, endpoint)
                WebDavSyncEngine(root, state).sync(transport, validated.stateIdentity)
        }
    }

    companion object {
        fun safeMessage(error: Throwable): String = when (error) {
            is WebDavHttpException -> error.message.orEmpty()
            else -> error.message?.takeIf(String::isNotBlank) ?: "无法连接 WebDAV 服务"
        }
    }
}

internal object WebDavEndpointFallback {
    fun <T> run(endpoints: List<WebDavEndpoint>, operation: (WebDavEndpoint) -> T): T {
        var lastError: Exception? = null
        endpoints.forEach { endpoint ->
            try {
                return operation(endpoint)
            } catch (error: Exception) {
                lastError = error
                if (error is WebDavHttpException && error.statusCode in setOf(401, 403)) throw error
            }
        }
        val failure = lastError ?: IllegalStateException("没有可用的 WebDAV 地址")
        throw IllegalStateException(WebDavSyncManager.safeMessage(failure), failure)
    }
}
