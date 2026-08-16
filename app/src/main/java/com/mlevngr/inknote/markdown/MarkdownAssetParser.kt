package com.mlevngr.inknote.markdown

/** Parses InkNote embeds while retaining legacy standard-Markdown imports. */
object MarkdownAssetParser {
    private val embed = Regex(
        """^\s*!\[\[asset:(assets/[^|\]]+)(?:\|([^|\]]*))?(?:\|mote-id:([A-Za-z0-9_-]+))?]]\s*$"""
    )
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
                    val instanceId = embedMatch.groupValues[3].ifBlank { null }
                    result += embeddedBlock(path, label, instanceId)
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

    data class AssetEmbed(
        val relativePath: String,
        val label: String,
        val instanceId: String?
    )

    fun parseAssetEmbed(source: String): AssetEmbed? {
        val match = embed.matchEntire(source) ?: return null
        return AssetEmbed(
            relativePath = match.groupValues[1],
            label = match.groupValues[2],
            instanceId = match.groupValues[3].ifBlank { null }
        )
    }

    fun withInstanceId(source: String, instanceId: String): String? {
        val parsed = parseAssetEmbed(source) ?: return null
        if (extension(parsed.relativePath) != "pdf") return null
        return buildString {
            append("![[asset:")
            append(parsed.relativePath)
            append('|')
            append(parsed.label)
            append("|mote-id:")
            append(instanceId)
            append("]]" )
        }
    }

    private fun embeddedBlock(
        path: String,
        label: String,
        instanceId: String? = null
    ): PreviewBlock = when (extension(path)) {
        "pdf" -> PreviewBlock.Pdf(path, label, instanceId)
        in imageExtensions -> PreviewBlock.Image(path, label)
        else -> PreviewBlock.Attachment(path, label)
    }

    private fun extension(path: String): String = path.substringAfterLast('.', "").lowercase()
}
