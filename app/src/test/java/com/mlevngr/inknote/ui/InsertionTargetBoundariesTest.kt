package com.mlevngr.inknote.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class InsertionTargetBoundariesTest {
    @Test fun exposesEveryBoundaryFromDocumentStartToEnd() {
        val rows = listOf(
            HybridRow.Rendered(0, PreviewRow.Markdown("First")),
            HybridRow.Editor(1, "Second"),
            HybridRow.Rendered(2, PreviewRow.Markdown("Third"))
        )

        assertEquals(listOf(0, 1, 2, 3), InsertionTargetBoundaries.from(rows))
    }

    @Test fun multiplePdfPagesShareOneBoundaryForTheirMarkdownLine() {
        val pdf = File("/tmp/document.pdf")
        val rows = listOf(
            HybridRow.Rendered(0, PreviewRow.Markdown("Before")),
            HybridRow.Rendered(1, PreviewRow.PdfPage(pdf, "PDF", 0, 2)),
            HybridRow.Rendered(1, PreviewRow.PdfPage(pdf, "PDF", 1, 2)),
            HybridRow.Rendered(2, PreviewRow.Markdown("After"))
        )

        assertEquals(listOf(0, 1, 2, 3), InsertionTargetBoundaries.from(rows))
    }
}
