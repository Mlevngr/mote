package com.mlevngr.inknote.ui

object NoteCanvasZoom {
    const val MIN_GESTURE_SCALE = 0.75f
    const val MAX_SCALE = 6f

    data class Transform(
        val scale: Float = 1f,
        val translationX: Float = 0f,
        val translationY: Float = 0f
    )

    fun update(
        current: Transform,
        factor: Float,
        previousFocusX: Float,
        previousFocusY: Float,
        focusX: Float,
        focusY: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ): Transform {
        val scale = (current.scale * factor).coerceIn(MIN_GESTURE_SCALE, MAX_SCALE)
        val appliedFactor = scale / current.scale
        return constrain(
            Transform(
                scale = scale,
                translationX = focusX - (previousFocusX - current.translationX) * appliedFactor,
                translationY = focusY - (previousFocusY - current.translationY) * appliedFactor
            ),
            viewportWidth,
            viewportHeight
        )
    }

    fun settle(
        transform: Transform,
        viewportWidth: Float,
        viewportHeight: Float
    ): Transform = if (transform.scale < 1f) {
        Transform()
    } else {
        constrain(transform, viewportWidth, viewportHeight)
    }

    fun constrain(
        transform: Transform,
        viewportWidth: Float,
        viewportHeight: Float
    ): Transform {
        if (transform.scale < 1f) {
            return transform.copy(
                translationX = viewportWidth * (1f - transform.scale) / 2f,
                translationY = viewportHeight * (1f - transform.scale) / 2f
            )
        }
        val minimumX = viewportWidth * (1f - transform.scale)
        val minimumY = viewportHeight * (1f - transform.scale)
        return transform.copy(
            translationX = transform.translationX.coerceIn(minimumX, 0f),
            translationY = transform.translationY.coerceIn(minimumY, 0f)
        )
    }

    fun previewRenderScale(scale: Float): Float = when {
        scale <= 1.25f -> 1f
        scale <= 1.75f -> 1.5f
        scale <= 2.5f -> 2f
        scale <= 3.5f -> 3f
        scale <= 5f -> 4f
        else -> 6f
    }
}
