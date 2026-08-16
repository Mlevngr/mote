package com.mlevngr.inknote.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginDocumentGuardTest {
    @Test
    fun `allows text restructuring while preserving embedded content`() {
        val original = """
            intro
            ![[asset:assets/photo.png|photo]]
            <!-- mote:pdf-note:one:0 -->
            page note
            <!-- /mote:pdf-note:one -->
        """.trimIndent()
        val proposed = """
            # Intro
            ![[asset:assets/photo.png|photo]]
            <!-- mote:pdf-note:one:0 -->
            **page note**
            <!-- /mote:pdf-note:one -->
        """.trimIndent()

        assertEquals(PluginDocumentGuard.ValidationResult.Valid, PluginDocumentGuard.validate(original, proposed))
    }

    @Test
    fun `rejects removal of attachment line`() {
        val result = PluginDocumentGuard.validate(
            "text\n![[asset:assets/file.pdf|file]]",
            "text"
        )

        assertTrue(result is PluginDocumentGuard.ValidationResult.Invalid)
    }

    @Test
    fun `rejects modification of page-note marker`() {
        val result = PluginDocumentGuard.validate(
            "<!-- mote:pdf-note:one:0 -->\nnote",
            "<!-- mote:pdf-note:two:0 -->\nnote"
        )

        assertTrue(result is PluginDocumentGuard.ValidationResult.Invalid)
    }

    @Test
    fun `rejects blank and oversized output`() {
        assertTrue(
            PluginDocumentGuard.validate("original", "   ") is PluginDocumentGuard.ValidationResult.Invalid
        )
        assertTrue(
            PluginDocumentGuard.validate("original", "x".repeat(2_000_001))
                is PluginDocumentGuard.ValidationResult.Invalid
        )
    }

    @Test
    fun `revision is stable and changes with content`() {
        assertEquals(PluginDocumentGuard.revision("same"), PluginDocumentGuard.revision("same"))
        assertTrue(PluginDocumentGuard.revision("same") != PluginDocumentGuard.revision("changed"))
    }
}
