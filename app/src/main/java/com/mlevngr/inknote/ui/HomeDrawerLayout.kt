package com.mlevngr.inknote.ui

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import kotlin.math.abs

/** DrawerLayout with an explicit, non-consuming left-edge swipe fallback. */
class HomeDrawerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : DrawerLayout(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val edgeSwipe = EdgeSwipeOpenDetector(
        edgeWidthPx = TOUCH_START_WIDTH_DP * density,
        triggerDistancePx = TRIGGER_DISTANCE_DP * density
    )
    private var drawerIsOpen = false

    init {
        addDrawerListener(object : SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                drawerIsOpen = true
                updateGestureExclusion()
            }

            override fun onDrawerClosed(drawerView: View) {
                drawerIsOpen = false
                updateGestureExclusion()
            }
        })
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!isDrawerOpen(GravityCompat.START) && edgeSwipe.onTouch(event.actionMasked, event.x, event.y)) {
            openDrawer(GravityCompat.START)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateGestureExclusion()
    }

    private fun updateGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        systemGestureExclusionRects = if (drawerIsOpen || width == 0 || height == 0) {
            emptyList()
        } else {
            listOf(
                Rect(
                    0,
                    0,
                    (SYSTEM_EDGE_EXCLUSION_WIDTH_DP * density).toInt(),
                    minOf(height, (SYSTEM_EDGE_EXCLUSION_HEIGHT_DP * density).toInt())
                )
            )
        }
    }

    private companion object {
        const val TOUCH_START_WIDTH_DP = 96f
        const val TRIGGER_DISTANCE_DP = 32f
        const val SYSTEM_EDGE_EXCLUSION_WIDTH_DP = 32f
        const val SYSTEM_EDGE_EXCLUSION_HEIGHT_DP = 196f
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
