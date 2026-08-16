package com.mlevngr.inknote.markdown

/** Hidden structural markers that keep ordinary Markdown lines anchored between PDF pages. */
object PdfPageNotes {
    private val startMarker = Regex(
        """^<!-- mote:pdf-note:([A-Za-z0-9_-]+):(\d+) -->$"""
    )
    private val endMarker = Regex("""^<!-- /mote:pdf-note:([A-Za-z0-9_-]+) -->$""")

    data class Anchor(val instanceId: String, val pageIndex: Int)

    data class Block(
        val anchor: Anchor,
        val startLine: Int,
        val contentLines: IntRange,
        val endLine: Int
    )

    data class Section(
        val embedLine: Int,
        val instanceId: String,
        val blocks: List<Block>,
        val endExclusive: Int
    )

    fun startMarker(instanceId: String, pageIndex: Int): String =
        "<!-- mote:pdf-note:$instanceId:$pageIndex -->"

    fun endMarker(instanceId: String): String = "<!-- /mote:pdf-note:$instanceId -->"

    fun isMarker(source: String): Boolean =
        endMarker.matches(source) || startMarker.matches(source)

    fun sectionAt(lines: List<String>, embedLine: Int): Section? {
        val embed = lines.getOrNull(embedLine)?.let(MarkdownAssetParser::parseAssetEmbed)
            ?: return null
        val instanceId = embed.instanceId ?: return null
        if (!embed.relativePath.endsWith(".pdf", ignoreCase = true)) return null

        val blocks = mutableListOf<Block>()
        var cursor = embedLine + 1
        while (cursor < lines.size) {
            val match = startMarker.matchEntire(lines[cursor]) ?: break
            if (match.groupValues[1] != instanceId) break
            val pageIndex = match.groupValues[2].toIntOrNull() ?: break
            val expectedEnd = endMarker(instanceId)
            val end = (cursor + 1 until lines.size)
                .firstOrNull { lines[it] == expectedEnd }
                ?: break
            blocks += Block(
                anchor = Anchor(instanceId, pageIndex),
                startLine = cursor,
                contentLines = (cursor + 1) until end,
                endLine = end
            )
            cursor = end + 1
        }
        return Section(embedLine, instanceId, blocks, cursor)
    }

    fun rekey(source: String, newInstanceId: String): String {
        val lines = source.split('\n').toMutableList()
        val embed = lines.firstOrNull()?.let(MarkdownAssetParser::parseAssetEmbed)
            ?: return source
        val oldId = embed.instanceId ?: return source
        lines[0] = MarkdownAssetParser.withInstanceId(lines[0], newInstanceId) ?: return source
        for (index in 1 until lines.size) {
            val start = startMarker.matchEntire(lines[index])
            val end = endMarker.matchEntire(lines[index])
            when {
                start?.groupValues?.get(1) == oldId -> {
                    lines[index] = startMarker(newInstanceId, start.groupValues[2].toInt())
                }
                end?.groupValues?.get(1) == oldId -> {
                    lines[index] = endMarker(newInstanceId)
                }
            }
        }
        return lines.joinToString("\n")
    }
}
