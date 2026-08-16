package com.mlevngr.inknote.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownHistoryTest {
    @Test fun nearbyTypingOnTheSameLineIsOneUndoEvent() {
        val history = history("", maxEvents = 100)
        history.record(state("h", 0, 1), MarkdownHistoryKind.Insert, 0, 100)
        history.record(state("he", 0, 2), MarkdownHistoryKind.Insert, 0, 300)
        history.record(state("hey", 0, 3), MarkdownHistoryKind.Insert, 0, 500)

        assertEquals("", history.undo()?.markdown)
        assertFalse(history.canUndo)
        assertEquals("hey", history.redo()?.markdown)
    }

    @Test fun pauseStartsANewTypingUndoEvent() {
        val history = history("")
        history.record(state("a", 0, 1), MarkdownHistoryKind.Insert, 0, 100)
        history.record(state("ab", 0, 2), MarkdownHistoryKind.Insert, 0, 700)

        assertEquals("a", history.undo()?.markdown)
        assertEquals("", history.undo()?.markdown)
    }

    @Test fun insertionAndDeletionNeverMergeTogether() {
        val history = history("a")
        history.record(state("ab", 0, 2), MarkdownHistoryKind.Insert, 0, 100)
        history.record(state("a", 0, 1), MarkdownHistoryKind.Delete, 0, 200)

        assertEquals("ab", history.undo()?.markdown)
        assertEquals("a", history.undo()?.markdown)
    }

    @Test fun structuralChangesAlwaysCreateSeparateEvents() {
        val history = history("one")
        history.record(state("one\ntwo", 1, 0), MarkdownHistoryKind.Structural, 1, 100)
        history.record(state("one\ntwo\nthree", 2, 0), MarkdownHistoryKind.Structural, 2, 200)

        assertEquals("one\ntwo", history.undo()?.markdown)
        assertEquals("one", history.undo()?.markdown)
    }

    @Test fun aNewEditAfterUndoClearsRedo() {
        val history = history("")
        history.record(state("a", 0, 1), MarkdownHistoryKind.Structural, 0, 100)
        history.record(state("ab", 0, 2), MarkdownHistoryKind.Structural, 0, 200)
        assertEquals("a", history.undo()?.markdown)

        history.record(state("ac", 0, 2), MarkdownHistoryKind.Structural, 0, 300)

        assertFalse(history.canRedo)
        assertNull(history.redo())
    }

    @Test fun historyRetainsAtMostTheConfiguredNumberOfUndoEvents() {
        val history = history("0", maxEvents = 2)
        history.record(state("1"), MarkdownHistoryKind.Structural, null, 100)
        history.record(state("2"), MarkdownHistoryKind.Structural, null, 200)
        history.record(state("3"), MarkdownHistoryKind.Structural, null, 300)

        assertEquals("2", history.undo()?.markdown)
        assertEquals("1", history.undo()?.markdown)
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)
    }

    @Test fun cursorCanChangeWithoutCreatingAnUndoEvent() {
        val history = history("same")

        assertTrue(history.updateCurrentState(state("same", 3, 4)))
        assertFalse(history.canUndo)
        assertEquals(3, history.recordedStateAfterOneEdit().activeLine)
    }

    private fun history(markdown: String, maxEvents: Int = 100) = MarkdownHistory(
        initialState = state(markdown),
        maxEvents = maxEvents,
        mergeDelayMillis = 500
    )

    private fun state(
        markdown: String,
        line: Int? = null,
        cursor: Int = 0
    ) = MarkdownHistoryState(markdown, line, cursor, cursor)

    private fun MarkdownHistory.recordedStateAfterOneEdit(): MarkdownHistoryState {
        record(state("changed", 3, 5), MarkdownHistoryKind.Structural, 3, 100)
        return undo()!!
    }
}
