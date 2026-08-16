package com.mlevngr.inknote.ui

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.isEmpty
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Reflows and scales the complete note surface while leaving app chrome at normal size. */
class NoteCanvasZoomLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {
    private var canvasScale = 1f
    private var pinching = false
    private var lastSpan = 0f
    private var reboundAnimator: ValueAnimator? = null

    val currentScale: Float get() = canvasScale

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        )
        if (isEmpty()) return
        val childWidth = (measuredWidth / canvasScale).roundToInt().coerceAtLeast(1)
        val childHeight = (measuredHeight / canvasScale).roundToInt().coerceAtLeast(1)
        getChildAt(0).measure(
            MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (isEmpty()) return
        val content = getChildAt(0)
        content.layout(0, 0, content.measuredWidth, content.measuredHeight)
        content.pivotX = 0f
        content.pivotY = 0f
        content.scaleX = canvasScale
        content.scaleY = canvasScale
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN && event.pointerCount >= 2) {
            beginPinch(event)
            return true
        }
        if (!pinching && event.pointerCount >= 2) {
            beginPinch(event)
        }
        return pinching
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 2) beginPinch(event)
            MotionEvent.ACTION_MOVE -> if (event.pointerCount >= 2) updatePinch(event)
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                finishPinch()
            }
            MotionEvent.ACTION_UP -> {
                finishPinch()
                performClick()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        reboundAnimator?.cancel()
        reboundAnimator = null
        super.onDetachedFromWindow()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun beginPinch(event: MotionEvent) {
        reboundAnimator?.cancel()
        reboundAnimator = null
        pinching = true
        lastSpan = span(event)
    }

    private fun updatePinch(event: MotionEvent) {
        val nextSpan = span(event)
        if (lastSpan > 0f && nextSpan > 0f) {
            setCanvasScale(NoteCanvasZoom.update(canvasScale, nextSpan / lastSpan))
        }
        lastSpan = nextSpan
    }

    private fun finishPinch() {
        if (!pinching) return
        pinching = false
        lastSpan = 0f
        val settled = NoteCanvasZoom.settle(canvasScale)
        if (settled == canvasScale) return
        reboundAnimator = ValueAnimator.ofFloat(canvasScale, settled).apply {
            duration = 180L
            addUpdateListener { setCanvasScale(it.animatedValue as Float) }
            start()
        }
    }

    private fun setCanvasScale(scale: Float) {
        if (scale == canvasScale) return
        canvasScale = scale
        requestLayout()
    }

    private fun span(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

    override fun generateLayoutParams(attrs: AttributeSet): LayoutParams =
        LayoutParams(context, attrs)

    override fun generateLayoutParams(params: LayoutParams): LayoutParams = LayoutParams(params)

}
