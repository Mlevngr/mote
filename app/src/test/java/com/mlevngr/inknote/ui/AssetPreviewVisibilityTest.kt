package com.mlevngr.inknote.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class AssetPreviewVisibilityTest {
    @Test fun collapsedPdfKeepsOneHeaderRowAndHidesRemainingPages() {
        val pdf = File("/tmp/document.pdf")
        val rows = (0 until 3).map { page ->
            HybridRow.Rendered(
                lineIndex = 2,
                preview = PreviewRow.PdfPage(pdf, "Document", page, 3)
            )
        }

        val visible = AssetPreviewVisibility.visibleRows(rows, setOf(pdf.canonicalPath))

        assertEquals(1, visible.size)
        assertEquals(0, ((visible.single() as HybridRow.Rendered).preview as PreviewRow.PdfPage).pageIndex)
    }

    @Test fun expandedPdfKeepsEveryPage() {
        val pdf = File("/tmp/document.pdf")
        val rows = (0 until 3).map { page ->
            HybridRow.Rendered(0, PreviewRow.PdfPage(pdf, "Document", page, 3))
        }

        assertEquals(3, AssetPreviewVisibility.visibleRows(rows, emptySet()).size)
    }

    @Test fun collapsingOnePdfDoesNotHideImagesOrOtherDocuments() {
        val first = File("/tmp/first.pdf")
        val second = File("/tmp/second.pdf")
        val image = File("/tmp/photo.jpg")
        val rows = listOf(
            HybridRow.Rendered(0, PreviewRow.PdfPage(first, "First", 0, 2)),
            HybridRow.Rendered(0, PreviewRow.PdfPage(first, "First", 1, 2)),
            HybridRow.Rendered(1, PreviewRow.Image(image, "Photo")),
            HybridRow.Rendered(2, PreviewRow.PdfPage(second, "Second", 0, 1))
        )

        assertEquals(
            3,
            AssetPreviewVisibility.visibleRows(rows, setOf(first.canonicalPath)).size
        )
    }
}
