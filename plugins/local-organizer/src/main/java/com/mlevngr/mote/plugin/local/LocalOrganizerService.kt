package com.mlevngr.mote.plugin.local

import com.mlevngr.mote.plugin.api.IMotePluginCallback
import com.mlevngr.mote.plugin.api.MotePluginService
import com.mlevngr.mote.plugin.api.PluginAction
import com.mlevngr.mote.plugin.api.PluginBundles
import com.mlevngr.mote.plugin.api.PluginCapability
import com.mlevngr.mote.plugin.api.PluginContract
import com.mlevngr.mote.plugin.api.PluginDescriptor
import com.mlevngr.mote.plugin.api.PluginError
import com.mlevngr.mote.plugin.api.PluginRequest
import com.mlevngr.mote.plugin.api.PluginResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class LocalOrganizerService : MotePluginService() {
    private val worker = Executors.newSingleThreadExecutor()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    override val pluginDescriptor = PluginDescriptor(
        apiVersion = PluginContract.API_VERSION,
        pluginId = "mote.local.organizer",
        label = "Mote 本地整理",
        description = "完全离线，以确定性规则整理 Markdown；不申请网络权限，也不上传笔记。",
        capabilities = setOf(
            PluginCapability.READ_FULL_NOTE,
            PluginCapability.MODIFY_TEXT,
            PluginCapability.MODIFY_STRUCTURE
        ),
        actions = listOf(
            PluginAction("organize", "本地结构化整理", "整理标题、列表、任务项、编号和重点字段"),
            PluginAction("tasks", "本地提取行动项", "从现有内容生成 Markdown 任务清单"),
            PluginAction("cleanup", "清理 Markdown 格式", "规范空行、列表与标题空格，不改写内容")
        )
    )

    override fun isCallerAllowed(uid: Int): Boolean =
        packageManager.getPackagesForUid(uid).orEmpty().contains(MOTE_PACKAGE)

    override fun execute(request: PluginRequest, callback: IMotePluginCallback) {
        cancelled.remove(request.requestId)
        worker.execute {
            if (request.requestId in cancelled) return@execute
            val result = runCatching {
                when (request.actionId) {
                    "organize" -> LocalMarkdownOrganizer.organize(request.markdown)
                    "tasks" -> LocalMarkdownOrganizer.extractTasks(request.markdown)
                    "cleanup" -> LocalMarkdownOrganizer.cleanup(request.markdown)
                    else -> error("不支持的本地整理操作：${request.actionId}")
                }
            }
            if (request.requestId in cancelled) return@execute
            result.onSuccess { markdown ->
                callback.onResult(
                    PluginBundles.result(
                        PluginResult(
                            request.sessionId,
                            request.requestId,
                            request.baseRevision,
                            markdown,
                            summary(request.actionId)
                        )
                    )
                )
            }.onFailure { error ->
                callback.onError(
                    PluginBundles.error(
                        PluginError(
                            request.sessionId,
                            request.requestId,
                            PluginContract.ERROR_PLUGIN_FAILURE,
                            error.message ?: "本地整理失败"
                        )
                    )
                )
            }
            cancelled.remove(request.requestId)
        }
    }

    override fun cancel(requestId: String) {
        cancelled += requestId
    }

    override fun onDestroy() {
        cancelled.clear()
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun summary(actionId: String): String = when (actionId) {
        "tasks" -> "已在本机提取行动项；未调用网络或 AI 服务。"
        "cleanup" -> "已在本机规范 Markdown 格式；未调用网络或 AI 服务。"
        else -> "已在本机整理标题、列表、编号和任务项；未调用网络或 AI 服务。"
    }

    private companion object {
        const val MOTE_PACKAGE = "com.mlevngr.inknote"
    }
}
