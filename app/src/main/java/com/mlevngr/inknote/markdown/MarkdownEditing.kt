package com.mlevngr.inknote.markdown

import kotlin.math.max
import kotlin.math.min

data class MarkdownEditResult(
    val source: String,
    val selectionStart: Int,
    val selectionEnd: Int
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
                safe <= location -> safe
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

    private data class ExistingPrefix(
        val value: String,
        val style: MarkdownBlockStyle?
    )
}
