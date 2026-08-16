package com.mlevngr.inknote.plugins

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.mlevngr.mote.plugin.api.IMotePlugin
import com.mlevngr.mote.plugin.api.IMotePluginCallback
import com.mlevngr.mote.plugin.api.PluginBundles
import com.mlevngr.mote.plugin.api.PluginContract
import com.mlevngr.mote.plugin.api.PluginDescriptor
import com.mlevngr.mote.plugin.api.PluginError
import com.mlevngr.mote.plugin.api.PluginRequest
import com.mlevngr.mote.plugin.api.PluginResult
import java.io.Closeable
import java.util.concurrent.Executors

class MotePluginHost(
    context: Context,
    private val onPluginsChanged: (List<DiscoveredPlugin>) -> Unit
) : Closeable {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val binderExecutor = Executors.newCachedThreadPool()
    private val activeConnections = mutableSetOf<PluginConnection>()
    private var receiverRegistered = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    fun start() {
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            ContextCompat.registerReceiver(
                appContext,
                packageReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            receiverRegistered = true
        }
        refresh()
    }

    fun refresh() {
        onPluginsChanged(discover())
    }

    fun loadDescriptor(
        plugin: DiscoveredPlugin,
        onLoaded: (PluginDescriptor) -> Unit,
        onError: (String) -> Unit
    ) {
        connect(plugin, DESCRIPTOR_TIMEOUT_MS, onError) { connection, remote ->
            binderExecutor.execute {
                val descriptor = runCatching {
                    PluginBundles.parseDescriptor(remote.descriptor)
                }.getOrNull()
                main.post {
                    if (!connection.finish()) return@post
                    if (
                        descriptor?.isCompatible() == true &&
                        descriptor.pluginId == plugin.pluginId &&
                        descriptor.apiVersion == plugin.apiVersion
                    ) onLoaded(descriptor)
                    else onError("插件 API 不兼容或描述信息无效")
                }
            }
        }
    }

    fun execute(
        plugin: DiscoveredPlugin,
        request: PluginRequest,
        onResult: (PluginResult) -> Unit,
        onError: (PluginError) -> Unit
    ) {
        connect(plugin, EXECUTION_TIMEOUT_MS, { message ->
            onError(
                PluginError(
                    request.sessionId,
                    request.requestId,
                    PluginContract.ERROR_PLUGIN_FAILURE,
                    message
                )
            )
        }, request.requestId) { connection, remote ->
            connection.remote = remote
            binderExecutor.execute {
                runCatching {
                    remote.execute(
                        PluginBundles.request(request),
                        object : IMotePluginCallback.Stub() {
                            override fun onResult(bundle: Bundle) {
                                val result = PluginBundles.parseResult(bundle) ?: return
                                main.post {
                                    if (connection.finish()) onResult(result)
                                }
                            }

                            override fun onError(bundle: Bundle) {
                                val error = PluginBundles.parseError(bundle) ?: return
                                main.post {
                                    if (connection.finish()) onError(error)
                                }
                            }
                        }
                    )
                }.onFailure { error ->
                    main.post {
                        if (connection.finish()) {
                            onError(
                                PluginError(
                                    request.sessionId,
                                    request.requestId,
                                    PluginContract.ERROR_PLUGIN_FAILURE,
                                    error.message ?: "插件调用失败"
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun cancel(requestId: String) {
        activeConnections.filter { it.requestId == requestId }.forEach { connection ->
            runCatching { connection.remote?.cancel(requestId) }
            connection.finish()
        }
    }

    private fun discover(): List<DiscoveredPlugin> {
        val intent = Intent(PluginContract.SERVICE_ACTION)
        val services = if (Build.VERSION.SDK_INT >= 33) {
            appContext.packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.queryIntentServices(intent, PackageManager.GET_META_DATA)
        }
        return services.mapNotNull { resolve ->
            val info = resolve.serviceInfo ?: return@mapNotNull null
            if (!info.exported) return@mapNotNull null
            val apiVersion = info.metaData?.getInt(PluginContract.META_API_VERSION) ?: return@mapNotNull null
            if (apiVersion !in 1..PluginContract.API_VERSION) return@mapNotNull null
            val pluginId = info.metaData?.getString(PluginContract.META_PLUGIN_ID)
                ?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            DiscoveredPlugin(
                component = ComponentName(info.packageName, info.name),
                packageName = info.packageName,
                pluginId = pluginId,
                label = resolve.loadLabel(appContext.packageManager).toString(),
                apiVersion = apiVersion
            )
        }.distinctBy { it.component }.sortedBy { it.label.lowercase() }
    }

    private fun connect(
        plugin: DiscoveredPlugin,
        timeoutMillis: Long,
        onError: (String) -> Unit,
        requestId: String? = null,
        onConnected: (PluginConnection, IMotePlugin) -> Unit
    ) {
        lateinit var connection: PluginConnection
        connection = PluginConnection(timeoutMillis, onError, onConnected)
        connection.requestId = requestId
        activeConnections += connection
        val bound = runCatching {
            appContext.bindService(
                Intent(PluginContract.SERVICE_ACTION).setComponent(plugin.component),
                connection,
                Context.BIND_AUTO_CREATE
            )
        }.getOrDefault(false)
        if (bound) connection.markBound()
        else if (connection.finish()) onError("无法连接插件服务")
    }

    override fun close() {
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(packageReceiver) }
            receiverRegistered = false
        }
        activeConnections.toList().forEach(PluginConnection::finish)
        binderExecutor.shutdownNow()
    }

    inner class PluginConnection(
        timeoutMillis: Long,
        private val onError: (String) -> Unit,
        private val onConnected: (PluginConnection, IMotePlugin) -> Unit
    ) : ServiceConnection, IBinder.DeathRecipient {
        var remote: IMotePlugin? = null
        var requestId: String? = null
        private var finished = false
        private var bound = false
        private var binder: IBinder? = null
        private val timeout = Runnable {
            if (finish()) onError("插件响应超时")
        }

        init {
            main.postDelayed(timeout, timeoutMillis)
        }

        override fun onServiceConnected(name: ComponentName?, serviceBinder: IBinder?) {
            if (finished || serviceBinder == null) return
            bound = true
            binder = serviceBinder
            runCatching { serviceBinder.linkToDeath(this, 0) }.onFailure {
                if (finish()) onError("插件连接已失效")
                return
            }
            val service = IMotePlugin.Stub.asInterface(serviceBinder)
            remote = service
            onConnected(this, service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (finish()) onError("插件已断开")
        }

        override fun onBindingDied(name: ComponentName?) {
            if (finish()) onError("插件已卸载或崩溃")
        }

        override fun binderDied() {
            main.post { if (finish()) onError("插件进程已退出") }
        }

        fun finish(): Boolean {
            if (finished) return false
            finished = true
            main.removeCallbacks(timeout)
            activeConnections.remove(this)
            binder?.let { serviceBinder ->
                runCatching { serviceBinder.unlinkToDeath(this, 0) }
            }
            binder = null
            if (bound) runCatching { appContext.unbindService(this) }
            return true
        }

        fun markBound() {
            bound = true
        }
    }

    private companion object {
        const val DESCRIPTOR_TIMEOUT_MS = 8_000L
        const val EXECUTION_TIMEOUT_MS = 90_000L
    }
}

data class DiscoveredPlugin(
    val component: ComponentName,
    val packageName: String,
    val pluginId: String,
    val label: String,
    val apiVersion: Int
)
