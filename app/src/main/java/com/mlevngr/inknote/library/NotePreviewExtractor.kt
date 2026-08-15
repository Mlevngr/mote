package com.mlevngr.inknote.library

import com.mlevngr.inknote.markdown.MarkdownAssetParser
import com.mlevngr.inknote.markdown.PreviewBlock

data class NotePreview(
    val excerpt: String,
    val renderSource: String = ""
)

object NotePreviewExtractor {
    private const val MAX_EXCERPT = 120

    fun extract(markdown: String): NotePreview {
        val plainText = buildString {
            MarkdownAssetParser.parse(markdown).forEach { block ->
                when (block) {
                    is PreviewBlock.Markdown -> appendMarkdownText(block.source)
                    else -> Unit
                }
            }
        }.replace(Regex("\\s+"), " ").trim()

        return NotePreview(
            excerpt = plainText.take(MAX_EXCERPT),
            renderSource = markdown.take(MAX_RENDER_SOURCE)
        )
    }

    private fun StringBuilder.appendMarkdownText(source: String) {
        source.lineSequence().forEach { line ->
            val cleaned = line
                .replace(Regex("^\\s{0,3}(#{1,6}|[-+*>]|\\d+[.)])\\s+"), "")
                .replace(Regex("!\\[[^]]*]\\([^)]*\\)"), "")
                .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
                .replace(Regex("[`*_~]"), "")
                .trim()
            if (cleaned.isNotEmpty()) {
                if (isNotEmpty()) append(' ')
                append(cleaned)
            }
        }
    }

    private const val MAX_RENDER_SOURCE = 8_192
}
