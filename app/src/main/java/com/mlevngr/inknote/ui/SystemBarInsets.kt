package com.mlevngr.inknote.ui

import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import kotlin.math.max

object SystemBarInsets {
    fun install(
        root: View,
        avoidIme: Boolean = false,
        topInsetTarget: View? = null,
        onInsetsChanged: (() -> Unit)? = null
    ) {
        val initial = Insets.of(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        val initialTargetTopMargin =
            (topInsetTarget?.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottom = if (avoidIme) {
                max(bars.bottom, windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            } else bars.bottom
            view.setPadding(
                initial.left + bars.left,
                initial.top + if (topInsetTarget == null) bars.top else 0,
                initial.right + bars.right,
                initial.bottom + bottom
            )
            topInsetTarget?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = initialTargetTopMargin + bars.top
            }
            onInsetsChanged?.invoke()
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
