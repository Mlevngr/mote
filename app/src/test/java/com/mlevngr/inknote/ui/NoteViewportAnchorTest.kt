package com.mlevngr.inknote.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteViewportAnchorTest {
    @Test fun insertionsAboveAnchorShiftItsLine() {
        val old = listOf("a", "b", "c", "d")
        assertEquals(3, NoteViewportAnchor.mapLine(old, listOf("a", "new", "b", "c", "d"), 2))
    }

    @Test fun deletionAboveAnchorDoesNotRestoreTheOldAdapterPosition() {
        assertEquals(1, NoteViewportAnchor.mapLine(listOf("a", "b", "c", "d"), listOf("a", "c", "d"), 2))
    }

    @Test fun deletedAnchorFallsBackToTheNextSurvivingLine() {
        assertEquals(1, NoteViewportAnchor.mapLine(listOf("a", "b", "c"), listOf("a", "c"), 1))
    }

    @Test fun deletingLastAnchorFallsBackToThePreviousLine() {
        assertEquals(0, NoteViewportAnchor.mapLine(listOf("a", "b"), listOf("a"), 1))
    }

    @Test fun repeatedLinesAreMappedBySurroundingEditNotFirstMatchingText() {
        val old = listOf("a", "same", "same", "tail")
        val new = listOf("new", "a", "same", "same", "tail")
        assertEquals(3, NoteViewportAnchor.mapLine(old, new, 2))
    }

    @Test fun changingTaskStateKeepsTheAnchorOnItsOwnLine() {
        assertEquals(1, NoteViewportAnchor.mapLine(listOf("head", "- [ ] task", "tail"),
            listOf("head", "- [x] task", "tail"), 1))
    }

    @Test fun unchangedDocumentAndEmptyDocumentAreSafe() {
        val lines = listOf("a", "b", "c")
        assertEquals(2, NoteViewportAnchor.mapLine(lines, lines, 2))
        assertEquals(0, NoteViewportAnchor.mapLine(lines, emptyList(), 2))
    }
}
