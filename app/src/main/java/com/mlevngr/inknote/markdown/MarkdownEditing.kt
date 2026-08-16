package com.mlevngr.inknote.markdown

import kotlin.math.max
import kotlin.math.min

data class MarkdownEditResult(
    val source: String,
    val selectionStart: Int,
    val selectionEnd: Int
)

data class OrderedListSplit(
    val currentLine: String,
    val nextLine: String
)

enum class MarkdownBlockStyle(val prefix: String) {
    Task("- [ ] "),
    Bullet("- "),
    Ordered("1. "),
    Quote("> ")
}

object MarkdownEditing {
    private val headingPrefix = Regex("^#{1,6}\\s+")
    private val taskPrefix = Regex("^[-+*]\\s+\\[[ xX]]\\s+")
    private val bulletPrefix = Regex("^[-+*]\\s+")
    private val orderedPrefix = Regex("^\\d+[.)]\\s+")
    private val orderedLine = Regex("^([ \\t]*)(\\d+)([.)])(\\s+)(.*)$")
    private val quotePrefix = Regex("^>\\s+")

    fun bold(source: String, selectionStart: Int, selectionEnd: Int): MarkdownEditResult =
        toggleWrap(source, selectionStart, selectionEnd, "**", "**")

    fun italic(source: String, selectionStart: Int, selectionEnd: Int): MarkdownEditResult =
        toggleWrap(source, selectionStart, selectionEnd, "_", "_")

    fun strikethrough(source: String, selectionStart: Int, selectionEnd: Int): MarkdownEditResult =
        toggleWrap(source, selectionStart, selectionEnd, "~~", "~~")

    fun inlineCode(source: String, selectionStart: Int, selectionEnd: Int): MarkdownEditResult =
        toggleWrap(source, selectionStart, selectionEnd, "`", "`")

    fun toggleBlock(
        source: String,
        selectionStart: Int,
        selectionEnd: Int,
        style: MarkdownBlockStyle
    ): MarkdownEditResult {
        val location = prefixLocation(source)
        val existing = blockPrefix(source.substring(location))
        val replacement = if (existing?.style == style) "" else style.prefix
        return replacePrefix(source, selectionStart, selectionEnd, location, existing?.value.orEmpty(), replacement)
    }

    fun setHeading(
        source: String,
        selectionStart: Int,
        selectionEnd: Int,
        level: Int
    ): MarkdownEditResult {
        require(level in 0..6) { "Heading level must be between 0 and 6" }
        val location = prefixLocation(source)
        val remainder = source.substring(location)
        val heading = headingPrefix.find(remainder)?.value
        val existing = heading ?: if (level > 0) blockPrefix(remainder)?.value else null
        val replacement = if (level == 0) "" else "#".repeat(level) + " "
        if (existing == null && level == 0) return unchanged(source, selectionStart, selectionEnd)
        return replacePrefix(
            source,
            selectionStart,
            selectionEnd,
            location,
            existing.orEmpty(),
            replacement
        )
    }

    fun headingLevel(source: String): Int {
        val match = headingPrefix.find(source.substring(prefixLocation(source))) ?: return 0
        return match.value.takeWhile { it == '#' }.length
    }

    fun isOrderedLine(source: String): Boolean = parseOrderedLine(source) != null

    fun orderedNumber(source: String): Int? = parseOrderedLine(source)?.number

    fun orderedIndent(source: String): String? = parseOrderedLine(source)?.indent

    fun orderedPrefixLength(source: String): Int? = parseOrderedLine(source)?.prefixLength

    fun orderedPrefix(source: String): String? = parseOrderedLine(source)?.let {
        source.substring(0, it.prefixLength)
    }

    fun splitOrderedLine(source: String, cursor: Int): OrderedListSplit? {
        val ordered = parseOrderedLine(source) ?: return null
        val splitAt = cursor.coerceIn(ordered.prefixLength, source.length)
        val current = source.substring(0, splitAt)
        val remainder = source.substring(splitAt).removePrefix(" ")
        val nextNumber = ordered.number + 1
        return OrderedListSplit(
            currentLine = current,
            nextLine = "${ordered.indent}$nextNumber${ordered.delimiter} $remainder"
        )
    }

    /**
     * Renumbers the contiguous ordered-list run containing [anchor]. A different indentation
     * level or any non-list line is a boundary. The first item's number is preserved so lists
     * intentionally starting above one keep their meaning.
     */
    fun renumberOrderedList(
        lines: List<String>,
        anchor: Int,
        startingNumber: Int? = null
    ): List<String> {
        if (anchor !in lines.indices) return lines
        val anchored = parseOrderedLine(lines[anchor]) ?: return lines
        var start = anchor
        while (start > 0 && parseOrderedLine(lines[start - 1])?.indent == anchored.indent) start--
        var end = anchor
        while (end < lines.lastIndex && parseOrderedLine(lines[end + 1])?.indent == anchored.indent) end++

        val firstNumber = startingNumber ?: parseOrderedLine(lines[start])?.number ?: return lines
        var changed = false
        val updated = lines.toMutableList()
        for (index in start..end) {
            val item = parseOrderedLine(lines[index]) ?: break
            val normalized = "${item.indent}${firstNumber + index - start}${item.delimiter} ${item.content}"
            if (normalized != lines[index]) {
                updated[index] = normalized
                changed = true
            }
        }
        return if (changed) updated else lines
    }

    fun adjustSelectionAfterOrderedRenumber(
        before: String,
        after: String,
        selectionStart: Int,
        selectionEnd: Int
    ): MarkdownEditResult {
        val oldLine = parseOrderedLine(before)
            ?: return unchanged(after, selectionStart, selectionEnd)
        val newLine = parseOrderedLine(after)
            ?: return unchanged(after, selectionStart, selectionEnd)
        fun adjusted(position: Int): Int {
            val safe = position.coerceIn(0, before.length)
            return when {
                safe < oldLine.indent.length -> safe
                safe <= oldLine.prefixLength -> newLine.prefixLength
                else -> safe - oldLine.prefixLength + newLine.prefixLength
            }.coerceIn(0, after.length)
        }
        return MarkdownEditResult(after, adjusted(selectionStart), adjusted(selectionEnd))
    }

    fun link(
        source: String,
        selectionStart: Int,
        selectionEnd: Int,
        labelPlaceholder: String,
        urlPlaceholder: String = "https://"
    ): MarkdownEditResult {
        val start = min(selectionStart, selectionEnd).coerceIn(0, source.length)
        val end = max(selectionStart, selectionEnd).coerceIn(start, source.length)
        val selected = source.substring(start, end)
        val label = selected.ifEmpty { labelPlaceholder }
        val insertion = "[$label]($urlPlaceholder)"
        val updated = source.replaceRange(start, end, insertion)
        return if (selected.isEmpty()) {
            MarkdownEditResult(updated, start + 1, start + 1 + label.length)
        } else {
            val urlStart = start + label.length + 3
            MarkdownEditResult(updated, urlStart, urlStart + urlPlaceholder.length)
        }
    }

    private fun toggleWrap(
        source: String,
        selectionStart: Int,
        selectionEnd: Int,
        opening: String,
        closing: String
    ): MarkdownEditResult {
        val start = min(selectionStart, selectionEnd).coerceIn(0, source.length)
        val end = max(selectionStart, selectionEnd).coerceIn(start, source.length)
        val wrapped = start >= opening.length && end + closing.length <= source.length &&
            source.regionMatches(start - opening.length, opening, 0, opening.length) &&
            source.regionMatches(end, closing, 0, closing.length)
        if (wrapped) {
            val updated = source.removeRange(end, end + closing.length)
                .removeRange(start - opening.length, start)
            return MarkdownEditResult(updated, start - opening.length, end - opening.length)
        }

        val selected = source.substring(start, end)
        val insertion = opening + selected + closing
        val updated = source.replaceRange(start, end, insertion)
        val contentStart = start + opening.length
        return MarkdownEditResult(updated, contentStart, contentStart + selected.length)
    }

    private fun prefixLocation(source: String): Int = source.indexOfFirst { it != ' ' && it != '\t' }
        .let { if (it == -1) source.length else it }

    private fun blockPrefix(source: String): ExistingPrefix? = when {
        headingPrefix.containsMatchIn(source) -> ExistingPrefix(
            headingPrefix.find(source)!!.value,
            null
        )
        taskPrefix.containsMatchIn(source) -> ExistingPrefix(
            taskPrefix.find(source)!!.value,
            MarkdownBlockStyle.Task
        )
        bulletPrefix.containsMatchIn(source) -> ExistingPrefix(
            bulletPrefix.find(source)!!.value,
            MarkdownBlockStyle.Bullet
        )
        orderedPrefix.containsMatchIn(source) -> ExistingPrefix(
            orderedPrefix.find(source)!!.value,
            MarkdownBlockStyle.Ordered
        )
        quotePrefix.containsMatchIn(source) -> ExistingPrefix(
            quotePrefix.find(source)!!.value,
            MarkdownBlockStyle.Quote
        )
        else -> null
    }

    private fun replacePrefix(
        source: String,
        selectionStart: Int,
        selectionEnd: Int,
        location: Int,
        existing: String,
        replacement: String
    ): MarkdownEditResult {
        val updated = source.replaceRange(location, location + existing.length, replacement)
        fun adjusted(position: Int): Int {
            val safe = position.coerceIn(0, source.length)
            return when {
                safe < location -> safe
                safe <= location + existing.length -> location + replacement.length
                else -> safe - existing.length + replacement.length
            }.coerceIn(0, updated.length)
        }
        return MarkdownEditResult(updated, adjusted(selectionStart), adjusted(selectionEnd))
    }

    private fun unchanged(source: String, selectionStart: Int, selectionEnd: Int) = MarkdownEditResult(
        source,
        selectionStart.coerceIn(0, source.length),
        selectionEnd.coerceIn(0, source.length)
    )

    private fun parseOrderedLine(source: String): OrderedLine? {
        val match = orderedLine.matchEntire(source) ?: return null
        val number = match.groupValues[2].toIntOrNull() ?: return null
        return OrderedLine(
            indent = match.groupValues[1],
            number = number,
            delimiter = match.groupValues[3],
            content = match.groupValues[5],
            prefixLength = match.groupValues.take(5).drop(1).sumOf(String::length)
        )
    }

    private data class ExistingPrefix(
        val value: String,
        val style: MarkdownBlockStyle?
    )

    private data class OrderedLine(
        val indent: String,
        val number: Int,
        val delimiter: String,
        val content: String,
        val prefixLength: Int
    )
}
