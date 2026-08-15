package com.mlevngr.inknote.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownEditingTest {
    @Test fun wrapsAndUnwrapsSelectedText() {
        val bold = MarkdownEditing.bold("hello world", 6, 11)
        assertEdit(bold, "hello **world**", 8, 13)

        val plain = MarkdownEditing.bold(bold.source, bold.selectionStart, bold.selectionEnd)
        assertEdit(plain, "hello world", 6, 11)
    }

    @Test fun insertsEmptyFormattingPairAtCursor() {
        assertEdit(MarkdownEditing.italic("text", 4, 4), "text__", 5, 5)
        assertEdit(MarkdownEditing.inlineCode("", 0, 0), "``", 1, 1)
    }

    @Test fun boldAndItalicCanBeNestedWithoutMarkerAmbiguity() {
        val italic = MarkdownEditing.italic("word", 0, 4)
        val bold = MarkdownEditing.bold(italic.source, italic.selectionStart, italic.selectionEnd)

        assertEdit(bold, "_**word**_", 3, 7)
    }

    @Test fun togglesTaskPrefixAndPreservesIndentation() {
        val task = MarkdownEditing.toggleBlock("  buy milk", 10, 10, MarkdownBlockStyle.Task)
        assertEdit(task, "  - [ ] buy milk", 16, 16)

        val plain = MarkdownEditing.toggleBlock(task.source, 16, 16, MarkdownBlockStyle.Task)
        assertEdit(plain, "  buy milk", 10, 10)
    }

    @Test fun replacesAnExistingBlockTypeInsteadOfStackingPrefixes() {
        val ordered = MarkdownEditing.toggleBlock("- item", 6, 6, MarkdownBlockStyle.Ordered)
        assertEdit(ordered, "1. item", 7, 7)

        val quote = MarkdownEditing.toggleBlock(ordered.source, 7, 7, MarkdownBlockStyle.Quote)
        assertEdit(quote, "> item", 6, 6)
    }

    @Test fun movesCursorBehindAInsertedPrefixOnAnEmptyLine() {
        MarkdownBlockStyle.entries.forEach { style ->
            val result = MarkdownEditing.toggleBlock("", 0, 0, style)
            assertEdit(result, style.prefix, style.prefix.length, style.prefix.length)
        }

        assertEdit(MarkdownEditing.setHeading("", 0, 0, 3), "### ", 4, 4)
    }

    @Test fun leavesCursorBeforeIndentationWhenItReallyPrecedesTheInsertionPoint() {
        val result = MarkdownEditing.toggleBlock("  item", 0, 0, MarkdownBlockStyle.Bullet)
        assertEdit(result, "  - item", 0, 0)
    }

    @Test fun headingMenuReplacesHeadingAndListPrefixes() {
        val heading = MarkdownEditing.setHeading("- Project", 9, 9, 2)
        assertEdit(heading, "## Project", 10, 10)
        assertEquals(2, MarkdownEditing.headingLevel(heading.source))

        val body = MarkdownEditing.setHeading(heading.source, 10, 10, 0)
        assertEdit(body, "Project", 7, 7)
        assertEquals(0, MarkdownEditing.headingLevel(body.source))
    }

    @Test fun linkUsesSelectionAsLabelAndSelectsUrl() {
        val result = MarkdownEditing.link("visit site", 6, 10, "链接文字")
        assertEdit(result, "visit [site](https://)", 13, 21)
    }

    @Test fun linkWithoutSelectionSelectsTheLabelPlaceholder() {
        val result = MarkdownEditing.link("", 0, 0, "链接文字")
        assertEdit(result, "[链接文字](https://)", 1, 5)
    }

    private fun assertEdit(
        result: MarkdownEditResult,
        source: String,
        selectionStart: Int,
        selectionEnd: Int
    ) {
        assertEquals(source, result.source)
        assertEquals(selectionStart, result.selectionStart)
        assertEquals(selectionEnd, result.selectionEnd)
    }
}
