package com.mlevngr.inknote.ui

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

object SystemBarInsets {
    fun install(
        root: View,
        avoidIme: Boolean = false,
        onInsetsChanged: (() -> Unit)? = null
    ) {
        val initial = Insets.of(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottom = if (avoidIme) {
                max(bars.bottom, windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            } else bars.bottom
            view.setPadding(
                initial.left + bars.left,
                initial.top + bars.top,
                initial.right + bars.right,
                initial.bottom + bottom
            )
            onInsetsChanged?.invoke()
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
