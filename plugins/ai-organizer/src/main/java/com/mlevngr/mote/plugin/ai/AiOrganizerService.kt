package com.mlevngr.mote.plugin.ai

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
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AiOrganizerService : MotePluginService() {
    private val worker = Executors.newFixedThreadPool(2)
    private val calls = ConcurrentHashMap<String, Call>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    override val pluginDescriptor = PluginDescriptor(
        apiVersion = PluginContract.API_VERSION,
        pluginId = "mote.ai.organizer",
        label = "Mote AI Organizer",
        description = "使用你配置的 AI 服务总结、整理和结构化 Markdown 笔记。",
        capabilities = setOf(
            PluginCapability.READ_FULL_NOTE,
            PluginCapability.MODIFY_TEXT,
            PluginCapability.MODIFY_STRUCTURE,
            PluginCapability.NETWORK_ACCESS
        ),
        actions = listOf(
            PluginAction("organize", "AI 整理整篇笔记", "建立多级标题、重点格式和列表结构"),
            PluginAction("summary", "生成摘要并整理", "在保留正文的同时添加摘要和结构"),
            PluginAction("tasks", "提取任务与行动项", "把可执行事项整理成 Markdown 任务列表")
        ),
        settingsActivity = AiSettingsActivity::class.java.name
    )

    override fun isCallerAllowed(uid: Int): Boolean =
        packageManager.getPackagesForUid(uid).orEmpty().contains(MOTE_PACKAGE)

    override fun execute(request: PluginRequest, callback: IMotePluginCallback) {
        cancelled.remove(request.requestId)
        worker.execute {
            try {
                val configuration = AiPluginSettings(this).load()
                if (configuration.endpoints.isEmpty() || configuration.model.isBlank()) {
                    sendError(
                        callback,
                        request,
                        PluginContract.ERROR_NOT_CONFIGURED,
                        "请先打开插件设置，填写至少一个 API 地址和模型名称。"
                    )
                    return@execute
                }
                var lastError = "AI 服务不可用"
                for (endpoint in configuration.endpoints) {
                    if (Thread.currentThread().isInterrupted || request.requestId in cancelled) {
                        return@execute
                    }
                    val result = runCatching { callAi(endpoint, configuration, request) }
                    result.onSuccess { markdown ->
                        if (request.requestId in cancelled) return@execute
                        callback.onResult(
                            PluginBundles.result(
                                PluginResult(
                                    request.sessionId,
                                    request.requestId,
                                    request.baseRevision,
                                    markdown,
                                    resultSummary(request.actionId)
                                )
                            )
                        )
                        return@execute
                    }.onFailure { lastError = it.message ?: lastError }
                }
                if (request.requestId !in cancelled) {
                    sendError(callback, request, PluginContract.ERROR_NETWORK, lastError)
                }
            } finally {
                calls.remove(request.requestId)
                cancelled.remove(request.requestId)
            }
        }
    }

    private fun callAi(
        endpoint: String,
        configuration: AiPluginConfiguration,
        pluginRequest: PluginRequest
    ): String {
        val body = JSONObject()
            .put("model", configuration.model)
            .put("temperature", 0.2)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt(pluginRequest.actionId)))
                    .put(JSONObject().put("role", "user").put("content", userPrompt(pluginRequest)))
            )
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val requestBuilder = Request.Builder()
            .url(AiTextProtocol.completionsUrl(endpoint))
            .post(body)
            .header("Content-Type", "application/json")
        if (configuration.apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${configuration.apiKey}")
        }
        val call = client.newCall(requestBuilder.build())
        calls[pluginRequest.requestId] = call
        call.execute().use { response ->
            val responseBody = response.body.string()
            check(response.isSuccessful) { "${response.code}: ${responseBody.take(300)}" }
            val content = JSONObject(responseBody)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            return AiTextProtocol.stripMarkdownFence(content).trim()
                .also { check(it.isNotEmpty()) { "AI 返回了空内容" } }
        }
    }

    override fun cancel(requestId: String) {
        cancelled += requestId
        calls.remove(requestId)?.cancel()
    }

    override fun onDestroy() {
        calls.values.forEach(Call::cancel)
        calls.clear()
        cancelled.clear()
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun sendError(
        callback: IMotePluginCallback,
        request: PluginRequest,
        code: String,
        message: String
    ) {
        callback.onError(
            PluginBundles.error(
                PluginError(request.sessionId, request.requestId, code, message)
            )
        )
    }

    private fun systemPrompt(actionId: String): String = """
        You are a Markdown document editor embedded in Mote. Return only the complete revised
        Markdown document, without code fences or explanations. Preserve every line containing
        "assets/" and every line beginning with "<!-- mote:" exactly and in the same order.
        Never invent attachment paths. Keep the user's language. Use concise hierarchical headings,
        bold for key terms, italic only for emphasis, bullet lists for parallel points, numbered lists
        for sequences, and task lists for actionable work. Do not discard factual content.
        Requested operation: $actionId.
    """.trimIndent()

    private fun userPrompt(request: PluginRequest): String = buildString {
        appendLine("Note title: ${request.noteTitle}")
        appendLine("Operation: ${request.actionId}")
        appendLine("--- DOCUMENT START ---")
        appendLine(request.markdown)
        append("--- DOCUMENT END ---")
    }

    private fun resultSummary(actionId: String): String = when (actionId) {
        "summary" -> "AI 已生成摘要并重新组织文档结构。"
        "tasks" -> "AI 已提取任务和行动项。"
        else -> "AI 已整理标题层级、重点和列表结构。"
    }

    private companion object {
        const val MOTE_PACKAGE = "com.mlevngr.inknote"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
