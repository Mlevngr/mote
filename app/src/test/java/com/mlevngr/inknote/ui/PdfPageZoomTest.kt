package com.mlevngr.inknote.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageZoomTest {
    @Test fun scaleIsLimitedAndKeepsTheGestureFocusStable() {
        val zoomed = PdfPageZoom.scale(
            state = PdfPageZoom.State(),
            factor = 2f,
            focusX = 75f,
            focusY = 50f,
            viewportWidth = 100f,
            viewportHeight = 100f
        )

        assertEquals(2f, zoomed.scale, 0.001f)
        assertEquals(-25f, zoomed.translationX, 0.001f)
        assertEquals(0f, zoomed.translationY, 0.001f)
        assertEquals(
            PdfPageZoom.MAX_SCALE,
            PdfPageZoom.scale(zoomed, 100f, 50f, 50f, 100f, 100f).scale,
            0.001f
        )
    }

    @Test fun panIsClampedToTheScaledPageBoundsAndResetIsExact() {
        val state = PdfPageZoom.State(scale = 3f)
        val panned = PdfPageZoom.pan(state, 1000f, -1000f, 100f, 200f)

        assertEquals(100f, panned.translationX, 0.001f)
        assertEquals(-200f, panned.translationY, 0.001f)
        assertTrue(PdfPageZoom.isZoomed(panned))
        assertEquals(PdfPageZoom.State(), PdfPageZoom.reset())
    }

    @Test fun shrinkingBelowFitWidthSpringsBackOnlyWhenTheGestureSettles() {
        val transient = PdfPageZoom.scale(
            PdfPageZoom.State(),
            factor = 0.5f,
            focusX = 50f,
            focusY = 50f,
            viewportWidth = 100f,
            viewportHeight = 100f
        )

        assertEquals(PdfPageZoom.MIN_GESTURE_SCALE, transient.scale, 0.001f)
        assertEquals(PdfPageZoom.State(), PdfPageZoom.settle(transient))
        assertEquals(
            PdfPageZoom.State(scale = 2f),
            PdfPageZoom.settle(PdfPageZoom.State(scale = 2f))
        )
    }
}
