package com.mlevngr.inknote.markdown

/** Parses InkNote embeds while retaining legacy standard-Markdown imports. */
object MarkdownAssetParser {
    private val embed = Regex("""^\s*!\[\[asset:(assets/[^|\]]+)(?:\|([^\]]*))?]]\s*$""")
    private val image = Regex("""^\s*!\[([^]]*)]\((assets/[^)]+)\)\s*$""")
    private val link = Regex("""^\s*\[([^]]*)]\((assets/[^)]+)\)\s*$""")
    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif")

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
            val embedMatch = embed.matchEntire(line)
            val imageMatch = image.matchEntire(line)
            val linkMatch = link.matchEntire(line)
            when {
                embedMatch != null -> {
                    flushMarkdown()
                    val path = embedMatch.groupValues[1]
                    val label = embedMatch.groupValues[2]
                    result += embeddedBlock(path, label)
                }
                imageMatch != null -> {
                    flushMarkdown()
                    val (alt, path) = imageMatch.destructured
                    result += if (extension(path) == "pdf") PreviewBlock.Pdf(path, alt)
                    else PreviewBlock.Image(path, alt)
                }
                linkMatch != null && extension(linkMatch.groupValues[2]) == "pdf" -> {
                    flushMarkdown()
                    result += PreviewBlock.Pdf(linkMatch.groupValues[2], linkMatch.groupValues[1])
                }
                else -> markdown.appendLine(line)
            }
        }
        flushMarkdown()
        return result
    }

    private fun embeddedBlock(path: String, label: String): PreviewBlock = when (extension(path)) {
        "pdf" -> PreviewBlock.Pdf(path, label)
        in imageExtensions -> PreviewBlock.Image(path, label)
        else -> PreviewBlock.Attachment(path, label)
    }

    private fun extension(path: String): String = path.substringAfterLast('.', "").lowercase()
}
