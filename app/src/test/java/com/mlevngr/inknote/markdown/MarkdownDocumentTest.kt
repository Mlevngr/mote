package com.mlevngr.inknote.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownDocumentTest {
    @Test fun splitsParagraphsAndRoundTripsNormalizedMarkdown() {
        val document = MarkdownDocument.parse("# Title\n\nFirst paragraph.\n\n- one\n- two")
        assertEquals(listOf("# Title", "First paragraph.", "- one\n- two"), document.snapshot())
        assertEquals("# Title\n\nFirst paragraph.\n\n- one\n- two", document.markdown())
    }

    @Test fun keepsBlankLinesInsideFencedCode() {
        val source = "```kotlin\nval a = 1\n\nval b = 2\n```\n\nAfter"
        assertEquals(
            listOf("```kotlin\nval a = 1\n\nval b = 2\n```", "After"),
            MarkdownDocument.parse(source).snapshot()
        )
    }

    @Test fun updatesAndInsertsBlocks() {
        val document = MarkdownDocument.parse("First\n\nThird")
        document.update(0, "Changed")
        assertEquals(1, document.insertAfter(0, "Second"))
        assertEquals("Changed\n\nSecond\n\nThird", document.markdown())
    }

    @Test fun emptyDocumentStillHasAnEditableBlock() {
        assertEquals(listOf(""), MarkdownDocument.parse("").snapshot())
    }
}
