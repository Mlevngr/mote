package com.mlevngr.inknote.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextSelectionTest {
    @Test
    fun `selects the word under the pressed offset`() {
        assertEquals(6..10, TextSelection.wordAt("hello world!", 8))
    }

    @Test
    fun `keeps underscore inside a word`() {
        assertEquals(0..8, TextSelection.wordAt("note_name", 4))
    }

    @Test
    fun `selects punctuation as a single character`() {
        assertEquals(5..5, TextSelection.wordAt("hello, world", 5))
    }

    @Test
    fun `clamps a press past the end of text`() {
        assertEquals(0..3, TextSelection.wordAt("note", 99))
    }

    @Test
    fun `returns no selection for empty text`() {
        assertNull(TextSelection.wordAt("", 0))
    }
}
