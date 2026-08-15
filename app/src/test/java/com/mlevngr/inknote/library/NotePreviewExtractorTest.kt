package com.mlevngr.inknote.library

import org.junit.Assert.assertEquals
import org.junit.Test

class NotePreviewExtractorTest {
    @Test fun extractsFirstInternalImageAndReadableMarkdownExcerpt() {
        val preview = NotePreviewExtractor.extract(
            "# Trip\n![[asset:assets/cover.jpg|Cover]]\nSee **Paris** and [map](https://example.com)."
        )

        assertEquals("Trip See Paris and map.", preview.excerpt)
        assertEquals(
            "# Trip\n![[asset:assets/cover.jpg|Cover]]\nSee **Paris** and [map](https://example.com).",
            preview.renderSource
        )
    }

    @Test fun ignoresPdfAndReturnsPlainTextWithoutAnImage() {
        val preview = NotePreviewExtractor.extract("[paper](assets/paper.pdf)\n- First item")

        assertEquals("First item", preview.excerpt)
    }

    @Test fun limitsExcerptLength() {
        val preview = NotePreviewExtractor.extract("a".repeat(10_000))
        assertEquals(120, preview.excerpt.length)
        assertEquals(8_192, preview.renderSource.length)
    }
}
