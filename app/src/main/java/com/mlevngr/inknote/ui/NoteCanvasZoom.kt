package com.mlevngr.inknote.ui

object NoteCanvasZoom {
    const val MIN_GESTURE_SCALE = 0.75f
    const val MAX_SCALE = 2.5f

    fun update(currentScale: Float, factor: Float): Float =
        (currentScale * factor).coerceIn(MIN_GESTURE_SCALE, MAX_SCALE)

    fun settle(scale: Float): Float = if (scale < 1f) 1f else scale
}
