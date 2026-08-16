package com.mlevngr.inknote.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteCanvasZoomTest {
    @Test fun pinchGeometricallyScalesAroundTheGestureFocus() {
        val result = update(NoteCanvasZoom.Transform(), factor = 2f)

        assertEquals(2f, result.scale, 0.001f)
        assertEquals(-500f, result.translationX, 0.001f)
        assertEquals(-400f, result.translationY, 0.001f)
    }

    @Test fun twoFingerMovementPansTheZoomedCanvas() {
        val result = update(
            current = NoteCanvasZoom.Transform(2f, -500f, -400f),
            factor = 1f,
            previousFocusX = 500f,
            previousFocusY = 400f,
            focusX = 550f,
            focusY = 425f
        )

        assertEquals(2f, result.scale, 0.001f)
        assertEquals(-450f, result.translationX, 0.001f)
        assertEquals(-375f, result.translationY, 0.001f)
    }

    @Test fun canvasScaleAndTranslationAreBounded() {
        val enlarged = update(NoteCanvasZoom.Transform(2f), factor = 100f)
        val reduced = update(NoteCanvasZoom.Transform(), factor = 0.1f)

        assertEquals(NoteCanvasZoom.MAX_SCALE, enlarged.scale, 0.001f)
        assertEquals(NoteCanvasZoom.MIN_GESTURE_SCALE, reduced.scale, 0.001f)
        assertEquals(125f, reduced.translationX, 0.001f)
        assertEquals(100f, reduced.translationY, 0.001f)
    }

    @Test fun releasingBelowFitWidthReturnsToIdentityButKeepsValidZoom() {
        val reduced = NoteCanvasZoom.Transform(0.8f, 100f, 80f)
        val enlarged = NoteCanvasZoom.Transform(1.75f, -200f, -100f)

        assertEquals(NoteCanvasZoom.Transform(), settle(reduced))
        assertEquals(enlarged, settle(enlarged))
    }

    @Test fun previewResolutionTracksLargeCanvasZoomInStableBuckets() {
        assertEquals(1f, NoteCanvasZoom.previewRenderScale(1f), 0.001f)
        assertEquals(2f, NoteCanvasZoom.previewRenderScale(2.2f), 0.001f)
        assertEquals(4f, NoteCanvasZoom.previewRenderScale(4.2f), 0.001f)
        assertEquals(6f, NoteCanvasZoom.previewRenderScale(5.5f), 0.001f)
    }

    private fun update(
        current: NoteCanvasZoom.Transform,
        factor: Float,
        previousFocusX: Float = 500f,
        previousFocusY: Float = 400f,
        focusX: Float = 500f,
        focusY: Float = 400f
    ) = NoteCanvasZoom.update(
        current = current,
        factor = factor,
        previousFocusX = previousFocusX,
        previousFocusY = previousFocusY,
        focusX = focusX,
        focusY = focusY,
        viewportWidth = 1000f,
        viewportHeight = 800f
    )

    private fun settle(transform: NoteCanvasZoom.Transform) = NoteCanvasZoom.settle(
        transform = transform,
        viewportWidth = 1000f,
        viewportHeight = 800f
    )
}
