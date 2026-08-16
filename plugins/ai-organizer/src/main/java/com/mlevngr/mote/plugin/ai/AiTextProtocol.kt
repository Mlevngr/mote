package com.mlevngr.mote.plugin.ai

internal object AiTextProtocol {
    fun completionsUrl(endpoint: String): String {
        val clean = endpoint.trim().trimEnd('/')
        return if (clean.endsWith("/v1/chat/completions")) clean else "$clean/v1/chat/completions"
    }

    fun stripMarkdownFence(content: String): String {
        val trimmed = content.trim()
        if (!trimmed.startsWith("```") || !trimmed.endsWith("```")) return trimmed
        return trimmed.substringAfter('\n').substringBeforeLast("```").trim()
    }
}
