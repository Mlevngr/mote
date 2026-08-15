package com.mlevngr.inknote.markdown

import kotlin.math.max
import kotlin.math.min

object MarkdownAutoPairing {
    private val pairs = mapOf(
        '[' to ']',
        '(' to ')',
        '{' to '}',
        '`' to '`',
        '"' to '"'
    )
    private val closing = pairs.values.toSet()

    fun type(
        source: String,
        selectionStart: Int,
        selectionEnd: Int,
        input: String
    ): MarkdownEditResult? {
        if (input.length != 1) return null
        val typed = input.single()
        val start = min(selectionStart, selectionEnd).coerceIn(0, source.length)
        val end = max(selectionStart, selectionEnd).coerceIn(start, source.length)

        if (start == end && typed in closing && source.getOrNull(start) == typed) {
            return MarkdownEditResult(source, start + 1, start + 1)
        }

        val matching = pairs[typed] ?: return null
        if (start > 0 && source[start - 1] == '\\') return null
        val selected = source.substring(start, end)
        val insertion = "$typed$selected$matching"
        val updated = source.replaceRange(start, end, insertion)
        return if (selected.isEmpty()) {
            MarkdownEditResult(updated, start + 1, start + 1)
        } else {
            MarkdownEditResult(updated, start + 1, end + 1)
        }
    }

    fun deleteEmptyPair(source: String, cursor: Int): MarkdownEditResult? {
        if (cursor !in 1 until source.length) return null
        val opening = source[cursor - 1]
        val closing = source[cursor]
        if (pairs[opening] != closing) return null
        return MarkdownEditResult(
            source.removeRange(cursor - 1, cursor + 1),
            cursor - 1,
            cursor - 1
        )
    }
}
