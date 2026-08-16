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

    @Test fun enterAfterOrderedItemContinuesWithTheNextNumber() {
        val result = MarkdownEditing.splitOrderedLine("1. first item", 13)

        assertEquals("1. first item", result?.currentLine)
        assertEquals("2. ", result?.nextLine)
    }

    @Test fun enterInTheMiddleMovesTheRemainderIntoTheNewOrderedItem() {
        val result = MarkdownEditing.splitOrderedLine("8) first second", 8)

        assertEquals("8) first", result?.currentLine)
        assertEquals("9) second", result?.nextLine)
    }

    @Test fun orderedSplitNeverPlacesTheCursorInsideItsPrefix() {
        val result = MarkdownEditing.splitOrderedLine("12. item", 1)

        assertEquals("12. ", result?.currentLine)
        assertEquals("13. item", result?.nextLine)
    }

    @Test fun renumbersAnEntireContiguousOrderedRun() {
        val result = MarkdownEditing.renumberOrderedList(
            listOf("1. Alpha", "7. Beta", "42. Gamma"),
            anchor = 1
        )

        assertEquals(listOf("1. Alpha", "2. Beta", "3. Gamma"), result)
    }

    @Test fun insertedMiddleItemBridgesAndRenumbersBothSides() {
        val result = MarkdownEditing.renumberOrderedList(
            listOf("1. Alpha", "1. Inserted", "2. Beta", "3. Gamma"),
            anchor = 1
        )

        assertEquals(listOf("1. Alpha", "2. Inserted", "3. Beta", "4. Gamma"), result)
    }

    @Test fun renumberingPreservesAnIntentionalStartingNumberAndDelimiter() {
        val result = MarkdownEditing.renumberOrderedList(
            listOf("5) Alpha", "20) Beta", "21) Gamma"),
            anchor = 2
        )

        assertEquals(listOf("5) Alpha", "6) Beta", "7) Gamma"), result)
    }

    @Test fun deletedOrderedItemCanSupplyTheNextSequencesStartingNumber() {
        val result = MarkdownEditing.renumberOrderedList(
            listOf("1. Alpha", "plain", "3. Gamma", "4. Delta"),
            anchor = 2,
            startingNumber = 2
        )

        assertEquals(listOf("1. Alpha", "plain", "2. Gamma", "3. Delta"), result)
    }

    @Test fun orderedIndentIsAvailableForDeletionBoundaryChecks() {
        assertEquals("  ", MarkdownEditing.orderedIndent("  2. Child"))
        assertEquals(null, MarkdownEditing.orderedIndent("plain"))
    }

    @Test fun renumberingStopsAtPlainTextAndDifferentIndentation() {
        val source = listOf("1. Parent", "  1. Child", "9. Parent", "plain", "4. Other")

        assertEquals(source, MarkdownEditing.renumberOrderedList(source, anchor = 0))
        assertEquals(source, MarkdownEditing.renumberOrderedList(source, anchor = 2))
        assertEquals(source, MarkdownEditing.renumberOrderedList(source, anchor = 4))
    }

    @Test fun cursorTracksAChangingOrderedPrefixWidth() {
        val result = MarkdownEditing.adjustSelectionAfterOrderedRenumber(
            before = "9. item",
            after = "10. item",
            selectionStart = 7,
            selectionEnd = 7
        )

        assertEdit(result, "10. item", 8, 8)
    }

    @Test fun cursorInsideARenumberedMarkerMovesBehindTheMarker() {
        val result = MarkdownEditing.adjustSelectionAfterOrderedRenumber(
            before = "  9. item",
            after = "  10. item",
            selectionStart = 3,
            selectionEnd = 3
        )

        assertEdit(result, "  10. item", 6, 6)
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
