package com.mlevngr.inknote.ui

import kotlin.math.max

object PdfPageZoom {
    const val MIN_GESTURE_SCALE = 0.75f
    const val MAX_SCALE = 5f

    data class State(
        val scale: Float = 1f,
        val translationX: Float = 0f,
        val translationY: Float = 0f
    )

    fun scale(
        state: State,
        factor: Float,
        focusX: Float,
        focusY: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): State {
        val nextScale = (state.scale * factor).coerceIn(MIN_GESTURE_SCALE, MAX_SCALE)
        val ratio = nextScale / state.scale
        val next = State(
            scale = nextScale,
            translationX = ratio * state.translationX +
                (1f - ratio) * (focusX - viewportWidth / 2f),
            translationY = ratio * state.translationY +
                (1f - ratio) * (focusY - viewportHeight / 2f)
        )
        return clamp(next, viewportWidth, viewportHeight)
    }

    fun pan(
        state: State,
        dx: Float,
        dy: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): State = clamp(
        state.copy(
            translationX = state.translationX + dx,
            translationY = state.translationY + dy
        ),
        viewportWidth,
        viewportHeight
    )

    fun settle(state: State): State = if (state.scale < 1f) reset() else state

    fun reset(): State = State()

    fun isZoomed(state: State): Boolean = state.scale > 1f

    private fun clamp(state: State, width: Float, height: Float): State {
        val maxX = max(0f, width * (state.scale - 1f) / 2f)
        val maxY = max(0f, height * (state.scale - 1f) / 2f)
        return state.copy(
            translationX = state.translationX.coerceIn(-maxX, maxX),
            translationY = state.translationY.coerceIn(-maxY, maxY)
        )
    }
}
