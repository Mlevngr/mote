package com.mlevngr.inknote.ui

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object SystemBarInsets {
    fun install(root: View) {
        val initial = Insets.of(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initial.left + bars.left,
                initial.top + bars.top,
                initial.right + bars.right,
                initial.bottom + bars.bottom
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
