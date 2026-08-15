package com.mlevngr.inknote.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotePreviewExtractorTest {
    @Test fun extractsFirstInternalImageAndReadableMarkdownExcerpt() {
        val preview = NotePreviewExtractor.extract(
            "# Trip\n![[asset:assets/cover.jpg|Cover]]\nSee **Paris** and [map](https://example.com)."
        )

        assertEquals("assets/cover.jpg", preview.imageRelativePath)
        assertEquals("Trip See Paris and map.", preview.excerpt)
    }

    @Test fun ignoresPdfAndReturnsPlainTextWithoutAnImage() {
        val preview = NotePreviewExtractor.extract("[paper](assets/paper.pdf)\n- First item")

        assertNull(preview.imageRelativePath)
        assertEquals("First item", preview.excerpt)
    }

    @Test fun limitsExcerptLength() {
        val preview = NotePreviewExtractor.extract("a".repeat(200))
        assertEquals(120, preview.excerpt.length)
    }
}
