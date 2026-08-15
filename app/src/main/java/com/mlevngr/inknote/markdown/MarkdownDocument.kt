package com.mlevngr.inknote.markdown

/** A lossless, line-oriented Markdown model used by the hybrid editor. */
class MarkdownDocument private constructor(private val lines: MutableList<String>) {
    val size: Int get() = lines.size

    operator fun get(index: Int): String = lines[index]

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
