package com.mlevngr.inknote.markdown

/**
 * Splits a Markdown document into native Markdown and embedded local asset blocks.
 *
 * Imported files use standard Markdown syntax so the note remains portable:
 * `![name](assets/id.png)` for images and `[name](assets/id.pdf)` for PDFs.
 */
object MarkdownAssetParser {
    private val image = Regex("""^\s*!\[([^]]*)]\((assets/[^)]+)\)\s*$""")
    private val link = Regex("""^\s*\[([^]]*)]\((assets/[^)]+)\)\s*$""")

    fun parse(source: String): List<PreviewBlock> {
        if (source.isEmpty()) return emptyList()
        val result = mutableListOf<PreviewBlock>()
        val markdown = StringBuilder()

        fun flushMarkdown() {
            if (markdown.isNotEmpty()) {
                result += PreviewBlock.Markdown(markdown.toString().trimEnd('\n'))
                markdown.clear()
            }
        }

        source.lineSequence().forEach { line ->
            val imageMatch = image.matchEntire(line)
            val linkMatch = link.matchEntire(line)
            when {
                imageMatch != null -> {
                    flushMarkdown()
                    val (alt, path) = imageMatch.destructured
                    result += if (path.endsWith(".pdf", ignoreCase = true)) {
                        PreviewBlock.Pdf(path, alt)
                    } else {
                        PreviewBlock.Image(path, alt)
                    }
                }
                linkMatch != null && linkMatch.groupValues[2].endsWith(
                    ".pdf",
                    ignoreCase = true
                ) -> {
                    flushMarkdown()
                    result += PreviewBlock.Pdf(
                        linkMatch.groupValues[2],
                        linkMatch.groupValues[1]
                    )
                }
                else -> markdown.appendLine(line)
            }
        }
        flushMarkdown()
        return result
    }
}
