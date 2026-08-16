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
        edgeWidthPx = Float.MAX_VALUE,
        triggerDistancePx = TRIGGER_DISTANCE_DP * density
    )
    private val excludedBounds = Rect()
    private var swipeExcludedView: View? = null
    private var nativeDrawerGestureSuppressed = false
    private var drawerIsOpen = false
    private var openSwipeEnabled = true

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

    fun excludeOpenSwipeFrom(view: View) {
        swipeExcludedView = view
    }

    fun setOpenSwipeEnabled(enabled: Boolean) {
        if (openSwipeEnabled == enabled) return
        openSwipeEnabled = enabled
        if (!enabled && isDrawerOpen(GravityCompat.START)) closeDrawer(GravityCompat.START)
        applyDrawerLockMode()
        updateGestureExclusion()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val startsInExcludedView =
            openSwipeEnabled && action == MotionEvent.ACTION_DOWN && isInsideExcludedView(event)
        if (startsInExcludedView) setNativeDrawerGestureSuppressed(true)
        if (
            openSwipeEnabled &&
            !isDrawerOpen(GravityCompat.START) &&
            edgeSwipe.onTouch(
                action,
                event.x,
                event.y,
                startAllowed = !startsInExcludedView
            )
        ) {
            openDrawer(GravityCompat.START)
        } else if (!openSwipeEnabled && action == MotionEvent.ACTION_DOWN) {
            edgeSwipe.onTouch(action, event.x, event.y, startAllowed = false)
        }
        val handled = super.dispatchTouchEvent(event)
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            setNativeDrawerGestureSuppressed(false)
        }
        return handled
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateGestureExclusion()
    }

    private fun updateGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        systemGestureExclusionRects = if (
            !openSwipeEnabled || drawerIsOpen || width == 0 || height == 0
        ) {
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

    private fun isInsideExcludedView(event: MotionEvent): Boolean {
        val excluded = swipeExcludedView ?: return false
        return excluded.isShown && excluded.getGlobalVisibleRect(excludedBounds) &&
            excludedBounds.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun setNativeDrawerGestureSuppressed(suppressed: Boolean) {
        if (nativeDrawerGestureSuppressed == suppressed) return
        nativeDrawerGestureSuppressed = suppressed
        applyDrawerLockMode()
    }

    private fun applyDrawerLockMode() {
        setDrawerLockMode(
            if (!openSwipeEnabled || nativeDrawerGestureSuppressed) {
                LOCK_MODE_LOCKED_CLOSED
            } else {
                LOCK_MODE_UNLOCKED
            },
            GravityCompat.START
        )
    }

    private companion object {
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

    fun onTouch(
        action: Int,
        x: Float,
        y: Float,
        startAllowed: Boolean = true
    ): Boolean = when (action) {
        MotionEvent.ACTION_DOWN -> {
            tracking = startAllowed && x <= edgeWidthPx
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
