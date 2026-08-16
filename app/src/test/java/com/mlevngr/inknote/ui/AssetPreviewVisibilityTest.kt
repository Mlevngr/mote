package com.mlevngr.inknote.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

        val visible = AssetPreviewVisibility.visibleRows(
            rows,
            setOf(AssetPreviewVisibility.AssetInstanceKey(2, pdf.canonicalPath))
        )

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
            AssetPreviewVisibility.visibleRows(
                rows,
                setOf(AssetPreviewVisibility.AssetInstanceKey(0, first.canonicalPath))
            ).size
        )
    }

    @Test fun repeatedReferencesToTheSamePdfCollapseIndependently() {
        val pdf = File("/tmp/repeated.pdf")
        val rows = listOf(
            HybridRow.Rendered(1, PreviewRow.PdfPage(pdf, "First", 0, 2)),
            HybridRow.Rendered(1, PreviewRow.PdfPage(pdf, "First", 1, 2)),
            HybridRow.Rendered(4, PreviewRow.PdfPage(pdf, "Second", 0, 2)),
            HybridRow.Rendered(4, PreviewRow.PdfPage(pdf, "Second", 1, 2))
        )

        val visible = AssetPreviewVisibility.visibleRows(
            rows,
            setOf(AssetPreviewVisibility.AssetInstanceKey(1, pdf.canonicalPath))
        )

        assertEquals(3, visible.size)
        assertEquals(listOf(1, 4, 4), visible.map(HybridRow::lineIndex))
    }

    @Test fun repeatedReferencesToTheSameImageHaveIndependentKeys() {
        val image = File("/tmp/repeated.jpg")
        val first = HybridRow.Rendered(1, PreviewRow.Image(image, "First"))
        val second = HybridRow.Rendered(4, PreviewRow.Image(image, "Second"))

        assertNotEquals(
            AssetPreviewVisibility.assetKey(first),
            AssetPreviewVisibility.assetKey(second)
        )
    }

    @Test fun collapsingAWholePdfAlsoHidesItsInterPageNotes() {
        val pdf = File("/tmp/document.pdf")
        val context = PdfRowContext("pdf:stable", 2, 0)
        val rows = listOf(
            HybridRow.Rendered(
                2,
                PreviewRow.PdfPage(pdf, "Document", 0, 2, "pdf:stable"),
                context
            ),
            HybridRow.Rendered(4, PreviewRow.Markdown("Page note"), context),
            HybridRow.Rendered(
                2,
                PreviewRow.PdfPage(pdf, "Document", 1, 2, "pdf:stable"),
                context.copy(pageIndex = 1)
            )
        )

        val visible = AssetPreviewVisibility.visibleRows(
            rows,
            setOf(AssetPreviewVisibility.AssetInstanceKey("pdf:stable"))
        )

        assertEquals(1, visible.size)
        assertTrue((visible.single() as HybridRow.Rendered).preview is PreviewRow.PdfPage)
    }

    @Test fun pageCollapseOnlyHidesThatPagePreview() {
        val first = PdfPreviewVisibility.PageKey("pdf:stable", 0)
        val second = PdfPreviewVisibility.PageKey("pdf:stable", 1)

        assertFalse(PdfPreviewVisibility.isPageExpanded(first, setOf(first)))
        assertTrue(PdfPreviewVisibility.isPageExpanded(second, setOf(first)))
    }

    @Test fun wholePdfCollapseAlsoHidesAPdfEmbeddedInsideItsPageNote() {
        val outer = File("/tmp/outer.pdf")
        val inner = File("/tmp/inner.pdf")
        val outerContext = PdfRowContext("pdf:outer", 0, 0)
        val rows = listOf(
            HybridRow.Rendered(
                0,
                PreviewRow.PdfPage(outer, "Outer", 0, 1, "pdf:outer"),
                outerContext
            ),
            HybridRow.Rendered(
                2,
                PreviewRow.PdfPage(inner, "Inner", 0, 1, "pdf:inner"),
                outerContext
            )
        )

        val visible = AssetPreviewVisibility.visibleRows(
            rows,
            setOf(AssetPreviewVisibility.AssetInstanceKey("pdf:outer"))
        )

        assertEquals(1, visible.size)
        assertEquals(outer, (visible.single() as HybridRow.Rendered).let {
            (it.preview as PreviewRow.PdfPage).file
        })
    }
}
