package com.mlevngr.inknote.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HybridRowFactoryTest {
    private val pdf = File("/tmp/paper.pdf")
    private val factory = HybridRowFactory { source ->
        when {
            source.startsWith("![[asset:") -> listOf(
                PreviewRow.PdfPage(pdf, "Paper", 0, 2, "pdf:stable"),
                PreviewRow.PdfPage(pdf, "Paper", 1, 2, "pdf:stable")
            )
            source.isEmpty() -> emptyList()
            else -> listOf(PreviewRow.Markdown(source))
        }
    }

    @Test fun placesMarkdownNotesAfterTheirAnchoredPdfPageAndHidesMarkers() {
        val rows = factory.create(
            listOf(
                "![[asset:assets/paper.pdf|Paper|mote-id:stable]]",
                "<!-- mote:pdf-note:stable:1 -->",
                "Between pages",
                "<!-- /mote:pdf-note:stable -->",
                "After PDF"
            ),
            activeLine = null
        )

        assertEquals(4, rows.size)
        assertEquals(
            listOf("page:0", "page:1", "markdown:Between pages", "markdown:After PDF"),
            rows.map { row ->
                when (val preview = (row as HybridRow.Rendered).preview) {
                    is PreviewRow.PdfPage -> "page:${preview.pageIndex}"
                    is PreviewRow.Markdown -> "markdown:${preview.source}"
                    else -> error("Unexpected row")
                }
            }
        )
        assertEquals(1, rows[2].pdfContext?.pageIndex)
        assertEquals(null, rows[3].pdfContext)
    }

    @Test fun pageNoteUsesTheExistingLineEditorWhenActive() {
        val rows = factory.create(
            listOf(
                "![[asset:assets/paper.pdf|Paper|mote-id:stable]]",
                "<!-- mote:pdf-note:stable:0 -->",
                "Editable note",
                "<!-- /mote:pdf-note:stable -->"
            ),
            activeLine = 2
        )

        val editor = rows.filterIsInstance<HybridRow.Editor>().single()
        assertEquals(2, editor.lineIndex)
        assertEquals("Editable note", editor.source)
        assertTrue(editor.pdfContext != null)
    }
}
