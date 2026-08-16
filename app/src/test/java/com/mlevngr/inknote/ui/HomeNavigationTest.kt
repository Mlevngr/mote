package com.mlevngr.inknote.ui

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeNavigationTest {
    @Test fun leftEdgeHorizontalSwipeOpensDrawer() {
        val detector = EdgeSwipeOpenDetector(edgeWidthPx = 32f, triggerDistancePx = 40f)

        assertFalse(detector.onTouch(MotionEvent.ACTION_DOWN, 12f, 200f))
        assertTrue(detector.onTouch(MotionEvent.ACTION_MOVE, 60f, 204f))
    }

    @Test fun movementOutsideEdgeOrMostlyVerticalDoesNotOpenDrawer() {
        val detector = EdgeSwipeOpenDetector(edgeWidthPx = 32f, triggerDistancePx = 40f)

        detector.onTouch(MotionEvent.ACTION_DOWN, 50f, 100f)
        assertFalse(detector.onTouch(MotionEvent.ACTION_MOVE, 120f, 102f))
        detector.onTouch(MotionEvent.ACTION_DOWN, 10f, 100f)
        assertFalse(detector.onTouch(MotionEvent.ACTION_MOVE, 55f, 180f))
    }

    @Test fun folderCardsRevealPartOfTheThirdCard() {
        val cardWidth = FolderStripSizing.cardWidth(
            viewportWidthPx = 1080,
            paddingStartPx = 24,
            paddingEndPx = 24,
            horizontalMarginsPx = 12
        )

        assertEquals(437, cardWidth)
        assertTrue((cardWidth + 12) * 2 < 1080 - 48)
        assertTrue((cardWidth + 12) * 3 > 1080 - 48)
    }
}
