package com.mlevngr.inknote.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteCanvasZoomTest {
    @Test fun pinchScaleIsBoundedForTheWholeNoteCanvas() {
        assertEquals(2f, NoteCanvasZoom.update(1f, 2f), 0.001f)
        assertEquals(
            NoteCanvasZoom.MAX_SCALE,
            NoteCanvasZoom.update(2f, 100f),
            0.001f
        )
        assertEquals(
            NoteCanvasZoom.MIN_GESTURE_SCALE,
            NoteCanvasZoom.update(1f, 0.1f),
            0.001f
        )
    }

    @Test fun releasingBelowFitWidthReturnsToFitButKeepsValidZoom() {
        assertEquals(1f, NoteCanvasZoom.settle(0.8f), 0.001f)
        assertEquals(1f, NoteCanvasZoom.settle(1f), 0.001f)
        assertEquals(1.75f, NoteCanvasZoom.settle(1.75f), 0.001f)
    }
}
