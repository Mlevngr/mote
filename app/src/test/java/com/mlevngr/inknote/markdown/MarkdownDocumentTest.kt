package com.mlevngr.inknote.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownDocumentTest {
    @Test fun preservesEveryMarkdownLineIncludingBlankLines() {
        val source = "# Title\n\nFirst paragraph.\n- one\n- two"
        val document = MarkdownDocument.parse(source)
        assertEquals(listOf("# Title", "", "First paragraph.", "- one", "- two"), document.snapshot())
        assertEquals(source, document.markdown())
    }

    @Test fun preservesTrailingBlankLine() {
        val document = MarkdownDocument.parse("First\n")
        assertEquals(listOf("First", ""), document.snapshot())
        assertEquals("First\n", document.markdown())
    }

    @Test fun updatesAndInsertsIndividualLines() {
        val document = MarkdownDocument.parse("First\nThird")
        document.update(0, "Changed")
        assertEquals(1, document.insertAfter(0, "Second"))
        assertEquals("Changed\nSecond\nThird", document.markdown())
    }

    @Test fun splitsAtCursorAndReturnsNewActiveLine() {
        val document = MarkdownDocument.parse("Hello world")
        assertEquals(1, document.splitLine(0, 5))
        assertEquals(listOf("Hello", " world"), document.snapshot())
    }

    @Test fun backspaceAtLineStartMergesWithPreviousLine() {
        val document = MarkdownDocument.parse("Hello\n world")
        assertEquals(5, document.mergeWithPrevious(1))
        assertEquals(listOf("Hello world"), document.snapshot())
        assertEquals(null, document.mergeWithPrevious(0))
    }

    @Test fun backspaceCannotMergeTheFirstBodyLineIntoTheSeparateTitle() {
        val document = MarkdownDocument.parse("Body")
        assertEquals(null, document.mergeWithPrevious(0))
        assertEquals("Body", document.markdown())
    }

    @Test fun replacesOneEditorLineWithPastedLines() {
        val document = MarkdownDocument.parse("Before\nOld\nAfter")
        assertEquals(1..3, document.replaceLine(1, listOf("One", "Two", "Three")))
        assertEquals("Before\nOne\nTwo\nThree\nAfter", document.markdown())
    }

    @Test fun emptyDocumentStillHasAnEditableLine() {
        assertEquals(listOf(""), MarkdownDocument.parse("").snapshot())
    }
}
