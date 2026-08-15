package com.mlevngr.inknote.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownAutoPairingTest {
    @Test fun insertsMatchingBracketAndPlacesCursorInside() {
        assertEdit(MarkdownAutoPairing.type("ab", 1, 1, "["), "a[]b", 2, 2)
        assertEdit(MarkdownAutoPairing.type("", 0, 0, "("), "()", 1, 1)
        assertEdit(MarkdownAutoPairing.type("", 0, 0, "{"), "{}", 1, 1)
    }

    @Test fun wrapsTheCurrentSelection() {
        assertEdit(MarkdownAutoPairing.type("word", 0, 4, "["), "[word]", 1, 5)
    }

    @Test fun skipsAnExistingClosingCharacter() {
        assertEdit(MarkdownAutoPairing.type("[]", 1, 1, "]"), "[]", 2, 2)
        assertEdit(MarkdownAutoPairing.type("\"\"", 1, 1, "\""), "\"\"", 2, 2)
    }

    @Test fun supportsMarkdownBackticksAndDoubleQuotes() {
        assertEdit(MarkdownAutoPairing.type("", 0, 0, "`"), "``", 1, 1)
        assertEdit(MarkdownAutoPairing.type("", 0, 0, "\""), "\"\"", 1, 1)
    }

    @Test fun backspaceBetweenAnEmptyPairDeletesBothCharacters() {
        assertEdit(MarkdownAutoPairing.deleteEmptyPair("a[]b", 2), "ab", 1, 1)
        assertEdit(MarkdownAutoPairing.deleteEmptyPair("``", 1), "", 0, 0)
    }

    @Test fun doesNotPairEscapedOrUnsupportedCharacters() {
        assertNull(MarkdownAutoPairing.type("\\", 1, 1, "["))
        assertNull(MarkdownAutoPairing.type("", 0, 0, "<"))
        assertNull(MarkdownAutoPairing.type("", 0, 0, "ab"))
        assertNull(MarkdownAutoPairing.deleteEmptyPair("[text]", 2))
    }

    private fun assertEdit(
        result: MarkdownEditResult?,
        source: String,
        selectionStart: Int,
        selectionEnd: Int
    ) {
        requireNotNull(result)
        assertEquals(source, result.source)
        assertEquals(selectionStart, result.selectionStart)
        assertEquals(selectionEnd, result.selectionEnd)
    }
}
