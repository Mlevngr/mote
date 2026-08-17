package com.mlevngr.inknote.markdown

data class MarkdownLineUpdateResult(
    val edit: MarkdownEditResult,
    val renumbered: Boolean
)

data class PdfPageNoteRemoval(
    val startLine: Int,
    val removedLineCount: Int,
    val focusLine: Int?,
    val focusCursor: Int
)

/** A lossless, line-oriented Markdown model used by the hybrid editor. */
class MarkdownDocument private constructor(private val lines: MutableList<String>) {
    val size: Int get() = lines.size

    operator fun get(index: Int): String = lines[index]

    fun getOrNull(index: Int): String? = lines.getOrNull(index)

    fun snapshot(): List<String> = lines.toList()

    fun update(index: Int, source: String) {
        require('\n' !in source && '\r' !in source) { "A line cannot contain a line break" }
        lines[index] = source
    }

    fun insertAfter(index: Int?, source: String): Int {
        require('\n' !in source && '\r' !in source) { "A line cannot contain a line break" }
        val position = if (index == null) lines.size else (index + 1).coerceAtMost(lines.size)
        lines.add(position, source)
        return position
    }

    fun splitLine(index: Int, cursor: Int): Int {
        val source = lines[index]
        val splitAt = cursor.coerceIn(0, source.length)
        lines[index] = source.substring(0, splitAt)
        lines.add(index + 1, source.substring(splitAt))
        return index + 1
    }

    fun renumberOrderedListAt(index: Int, startingNumber: Int? = null): Boolean {
        val updated = MarkdownEditEngine.renumberOrderedList(lines, index, startingNumber)
        if (updated === lines || updated == lines) return false
        lines.clear()
        lines.addAll(updated)
        return true
    }

    /**
     * Updates one editor line and reconciles ordered-list numbering when its marker changes.
     * Normal content typing does not scan the surrounding list.
     */
    fun updateWithOrderedListReconciliation(
        index: Int,
        source: String,
        selectionStart: Int,
        selectionEnd: Int
    ): MarkdownLineUpdateResult {
        val previous = lines[index]
        val previousNumber = MarkdownEditEngine.orderedNumber(previous)
        val previousIndent = MarkdownEditEngine.orderedIndent(previous)
        val previousPrefix = MarkdownEditEngine.orderedPrefix(previous)
        update(index, source)

        val currentIndent = MarkdownEditEngine.orderedIndent(source)
        val currentPrefix = MarkdownEditEngine.orderedPrefix(source)
        var renumbered = false

        // Moving out of an existing list run closes the gap left in that run.
        if (previousNumber != null && previousIndent != currentIndent) {
            val nextIndent = getOrNull(index + 1)?.let(MarkdownEditEngine::orderedIndent)
            if (nextIndent == previousIndent) {
                renumbered = renumberOrderedListAt(index + 1, startingNumber = previousNumber)
            }
        }

        // Adding, restoring or changing a marker joins and normalizes the new list run.
        if (currentPrefix != null && currentPrefix != previousPrefix) {
            renumbered = renumberOrderedListAt(index) || renumbered
        }

        val finalSource = lines[index]
        val edit = if (finalSource == source) {
            MarkdownEditResult(source, selectionStart, selectionEnd)
        } else {
            MarkdownEditEngine.adjustSelectionAfterOrderedRenumber(
                source,
                finalSource,
                selectionStart,
                selectionEnd
            )
        }
        return MarkdownLineUpdateResult(edit, renumbered)
    }

    /** Joins this line into the previous line and returns the new cursor position. */
    fun mergeWithPrevious(index: Int): Int? {
        if (index !in 1 until lines.size) return null
        val cursor = lines[index - 1].length
        lines[index - 1] += lines[index]
        lines.removeAt(index)
        return cursor
    }

    fun replaceLine(index: Int, replacement: List<String>): IntRange {
        require(replacement.isNotEmpty()) { "Replacement must contain at least one line" }
        require(replacement.none { '\n' in it || '\r' in it }) {
            "Replacement entries cannot contain line breaks"
        }
        lines.removeAt(index)
        lines.addAll(index, replacement)
        return index until index + replacement.size
    }

    fun removeLine(index: Int) {
        require(index in lines.indices) { "Line index is out of bounds" }
        if (lines.size == 1) lines[0] = "" else lines.removeAt(index)
    }

    fun insertPdfPageNote(
        embedLine: Int,
        pageIndex: Int,
        newInstanceId: () -> String
    ): Int {
        require(pageIndex >= 0) { "Page index cannot be negative" }
        val parsed = lines.getOrNull(embedLine)?.let(MarkdownAssetParser::parseAssetEmbed)
            ?: error("Line is not an embedded asset")
        require(parsed.relativePath.endsWith(".pdf", ignoreCase = true)) {
            "Asset is not a PDF"
        }
        val instanceId = parsed.instanceId ?: newInstanceId().also { id ->
            lines[embedLine] = requireNotNull(MarkdownAssetParser.withInstanceId(lines[embedLine], id))
        }
        val section = requireNotNull(PdfPageNotes.sectionAt(lines, embedLine))
        section.blocks.firstOrNull { it.anchor.pageIndex == pageIndex }?.let { block ->
            val firstBlank = block.contentLines.firstOrNull { lines[it].isBlank() }
            if (firstBlank != null) return firstBlank
            if (!block.contentLines.isEmpty()) return block.contentLines.first
            lines.add(block.endLine, "")
            return block.endLine
        }

        val insertion = section.blocks.firstOrNull { it.anchor.pageIndex > pageIndex }
            ?.startLine
            ?: section.endExclusive
        lines.addAll(
            insertion,
            listOf(
                PdfPageNotes.startMarker(instanceId, pageIndex),
                "",
                PdfPageNotes.endMarker(instanceId)
            )
        )
        return insertion + 1
    }

    fun assetBlockSource(embedLine: Int): String {
        require(embedLine in lines.indices) { "Line index is out of bounds" }
        val section = PdfPageNotes.sectionAt(lines, embedLine)
        val endExclusive = section?.endExclusive ?: (embedLine + 1)
        return lines.subList(embedLine, endExclusive).joinToString("\n")
    }

    /** Removes an embed and its contiguous PDF page-note blocks. Returns removed line count. */
    fun removeAssetBlock(embedLine: Int): Int {
        require(embedLine in lines.indices) { "Line index is out of bounds" }
        val endExclusive = PdfPageNotes.sectionAt(lines, embedLine)?.endExclusive ?: (embedLine + 1)
        val count = endExclusive - embedLine
        repeat(count) { lines.removeAt(embedLine) }
        if (lines.isEmpty()) lines += ""
        return count
    }

    /** Removes a blank page-note line, including its container when no content remains. */
    fun removeEmptyPdfPageNoteAt(contentLine: Int): PdfPageNoteRemoval? {
        val block = PdfPageNotes.blockContaining(lines, contentLine) ?: return null
        if (!lines[contentLine].isBlank()) return null
        if (block.contentLines.any { !lines[it].isBlank() }) {
            lines.removeAt(contentLine)
            val nextContentLine = contentLine.takeIf { it < block.endLine - 1 }
            val previousContentLine = (contentLine - 1).takeIf { it in block.contentLines }
            val focus = nextContentLine ?: previousContentLine
            return PdfPageNoteRemoval(
                startLine = contentLine,
                removedLineCount = 1,
                focusLine = focus,
                focusCursor = if (focus == previousContentLine) {
                    previousContentLine?.let { lines[it].length } ?: 0
                } else 0
            )
        }

        val count = block.endLine - block.startLine + 1
        repeat(count) { lines.removeAt(block.startLine) }
        if (lines.isEmpty()) lines += ""

        val forward = (block.startLine until lines.size).firstOrNull(::isEditableContentLine)
        if (forward != null) {
            return PdfPageNoteRemoval(block.startLine, count, forward, 0)
        }
        val backward = (block.startLine - 1 downTo 0).firstOrNull(::isEditableContentLine)
        return PdfPageNoteRemoval(
            startLine = block.startLine,
            removedLineCount = count,
            focusLine = backward,
            focusCursor = backward?.let { lines[it].length } ?: 0
        )
    }

    private fun isEditableContentLine(index: Int): Boolean {
        val source = lines[index]
        return !PdfPageNotes.isMarker(source) && MarkdownAssetParser.parseAssetEmbed(source) == null
    }

    /** Pastes source at an exact boundary between lines. Boundary 0 is document start. */
    fun pasteAtInsertion(boundaryIndex: Int, source: String): IntRange {
        require(boundaryIndex in 0..lines.size) { "Insertion boundary is out of bounds" }
        val replacement = source.replace("\r\n", "\n").replace('\r', '\n')
            .split('\n', ignoreCase = false, limit = Int.MAX_VALUE)
        if (lines.size == 1 && lines[0].isBlank()) return replaceLine(0, replacement)

        lines.addAll(boundaryIndex, replacement)
        return boundaryIndex until boundaryIndex + replacement.size
    }

    /** Pastes into a blank target line, or directly after a non-blank target line. */
    fun pasteAt(targetLine: Int?, source: String): IntRange {
        val replacement = source.replace("\r\n", "\n").replace('\r', '\n')
            .split('\n', ignoreCase = false, limit = Int.MAX_VALUE)
        val target = targetLine?.coerceIn(lines.indices)
        if (target != null && lines[target].isBlank()) return replaceLine(target, replacement)

        if (target == null && lines.size == 1 && lines[0].isBlank()) {
            return replaceLine(0, replacement)
        }
        val insertion = if (target == null) lines.size else target + 1
        lines.addAll(insertion, replacement)
        return insertion until insertion + replacement.size
    }

    fun markdown(): String = lines.joinToString("\n")

    companion object {
        fun parse(source: String): MarkdownDocument = MarkdownDocument(
            source.split('\n', ignoreCase = false, limit = Int.MAX_VALUE).toMutableList()
                .ifEmpty { mutableListOf("") }
        )
    }
}
