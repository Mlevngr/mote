package com.mlevngr.inknote.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import kotlin.math.abs

/** DrawerLayout with an explicit, non-consuming left-edge swipe fallback. */
class HomeDrawerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : DrawerLayout(context, attrs, defStyleAttr) {
    private val edgeSwipe = EdgeSwipeOpenDetector(
        edgeWidthPx = 32f * resources.displayMetrics.density,
        triggerDistancePx = 40f * resources.displayMetrics.density
    )

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!isDrawerOpen(GravityCompat.START) && edgeSwipe.onTouch(event.actionMasked, event.x, event.y)) {
            openDrawer(GravityCompat.START)
        }
        return super.dispatchTouchEvent(event)
    }
}

internal class EdgeSwipeOpenDetector(
    private val edgeWidthPx: Float,
    private val triggerDistancePx: Float
) {
    private var tracking = false
    private var downX = 0f
    private var downY = 0f

    fun onTouch(action: Int, x: Float, y: Float): Boolean = when (action) {
        MotionEvent.ACTION_DOWN -> {
            tracking = x <= edgeWidthPx
            downX = x
            downY = y
            false
        }

        MotionEvent.ACTION_MOVE -> {
            if (!tracking) {
                false
            } else {
                val dx = x - downX
                val dy = abs(y - downY)
                val shouldOpen = dx >= triggerDistancePx && dx > dy * HORIZONTAL_DOMINANCE
                if (shouldOpen) tracking = false
                shouldOpen
            }
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            tracking = false
            false
        }

        else -> false
    }

    private companion object {
        const val HORIZONTAL_DOMINANCE = 1.25f
    }
}
