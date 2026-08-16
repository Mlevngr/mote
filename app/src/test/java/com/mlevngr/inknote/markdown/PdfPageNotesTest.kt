package com.mlevngr.inknote.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageNotesTest {
    @Test fun insertsAStableInstanceAndAHiddenMarkdownNoteBlock() {
        val document = MarkdownDocument.parse(
            "![[asset:assets/paper.pdf|Paper]]\nAfter"
        )

        val noteLine = document.insertPdfPageNote(0, 1) { "fixed-id" }

        assertEquals(2, noteLine)
        assertEquals(
            "![[asset:assets/paper.pdf|Paper|mote-id:fixed-id]]\n" +
                "<!-- mote:pdf-note:fixed-id:1 -->\n\n" +
                "<!-- /mote:pdf-note:fixed-id -->\nAfter",
            document.markdown()
        )
    }

    @Test fun reusesAnExistingPageNoteAndKeepsPageBlocksOrdered() {
        val document = MarkdownDocument.parse(
            "![[asset:assets/paper.pdf|Paper]]\nAfter"
        )
        val pageThree = document.insertPdfPageNote(0, 2) { "fixed-id" }
        document.update(pageThree, "Third-page note")

        val pageOne = document.insertPdfPageNote(0, 0) { "unused" }
        val pageThreeAgain = document.insertPdfPageNote(0, 2) { "unused" }

        assertTrue(pageOne < pageThreeAgain)
        assertEquals("Third-page note", document[pageThreeAgain])
        assertEquals(pageThreeAgain, document.insertPdfPageNote(0, 2) { "unused" })
    }

    @Test fun assetBlockCopyIncludesPageNotesAndCanBeRekeyed() {
        val source = """![[asset:assets/paper.pdf|Paper|mote-id:original]]
            |<!-- mote:pdf-note:original:0 -->
            |A **Markdown** note
            |<!-- /mote:pdf-note:original -->
            |After
        """.trimMargin()
        val document = MarkdownDocument.parse(source)

        val block = document.assetBlockSource(0)
        val copy = PdfPageNotes.rekey(block, "copy")

        assertTrue(block.contains("mote-id:original"))
        assertTrue(copy.contains("mote-id:copy"))
        assertTrue(copy.contains("mote:pdf-note:copy:0"))
        assertFalse(copy.contains("original"))
        assertNotEquals(block, copy)
    }

    @Test fun removingAnAssetRemovesItsPageNotesButKeepsFollowingText() {
        val document = MarkdownDocument.parse(
            """![[asset:assets/paper.pdf|Paper|mote-id:pdf-1]]
                |<!-- mote:pdf-note:pdf-1:0 -->
                |Note
                |<!-- /mote:pdf-note:pdf-1 -->
                |After
            """.trimMargin()
        )

        assertEquals(4, document.removeAssetBlock(0))
        assertEquals("After", document.markdown())
    }
}
