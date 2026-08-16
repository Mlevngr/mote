package com.mlevngr.inknote.ui

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.isEmpty
import kotlin.math.hypot

/** Geometrically zooms the complete note surface without changing its layout or text size. */
class NoteCanvasZoomLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {
    private var transform = NoteCanvasZoom.Transform()
    private var pinching = false
    private var lastSpan = 0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var reboundAnimator: ValueAnimator? = null

    val currentScale: Float get() = transform.scale

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        )
        if (isEmpty()) return
        getChildAt(0).measure(
            MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (isEmpty()) return
        val content = getChildAt(0)
        content.layout(0, 0, content.measuredWidth, content.measuredHeight)
        content.pivotX = 0f
        content.pivotY = 0f
        applyTransform()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        transform = NoteCanvasZoom.constrain(transform, width.toFloat(), height.toFloat())
        applyTransform()
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
        lastFocusX = focusX(event)
        lastFocusY = focusY(event)
    }

    private fun updatePinch(event: MotionEvent) {
        val nextSpan = span(event)
        if (lastSpan > 0f && nextSpan > 0f) {
            val nextFocusX = focusX(event)
            val nextFocusY = focusY(event)
            setTransform(
                NoteCanvasZoom.update(
                    current = transform,
                    factor = nextSpan / lastSpan,
                    previousFocusX = lastFocusX,
                    previousFocusY = lastFocusY,
                    focusX = nextFocusX,
                    focusY = nextFocusY,
                    viewportWidth = width.toFloat(),
                    viewportHeight = height.toFloat()
                )
            )
            lastFocusX = nextFocusX
            lastFocusY = nextFocusY
        }
        lastSpan = nextSpan
    }

    private fun finishPinch() {
        if (!pinching) return
        pinching = false
        lastSpan = 0f
        val settled = NoteCanvasZoom.settle(transform, width.toFloat(), height.toFloat())
        if (settled == transform) return
        val start = transform
        reboundAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180L
            addUpdateListener {
                val progress = it.animatedValue as Float
                setTransform(
                    NoteCanvasZoom.Transform(
                        scale = lerp(start.scale, settled.scale, progress),
                        translationX = lerp(start.translationX, settled.translationX, progress),
                        translationY = lerp(start.translationY, settled.translationY, progress)
                    )
                )
            }
            start()
        }
    }

    private fun setTransform(value: NoteCanvasZoom.Transform) {
        if (value == transform) return
        transform = value
        applyTransform()
    }

    private fun applyTransform() {
        if (isEmpty()) return
        getChildAt(0).apply {
            scaleX = transform.scale
            scaleY = transform.scale
            translationX = transform.translationX
            translationY = transform.translationY
        }
    }

    private fun span(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
    }

    private fun focusX(event: MotionEvent): Float = (event.getX(0) + event.getX(1)) / 2f

    private fun focusY(event: MotionEvent): Float = (event.getY(0) + event.getY(1)) / 2f

    private fun lerp(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

    override fun generateLayoutParams(attrs: AttributeSet): LayoutParams =
        LayoutParams(context, attrs)

    override fun generateLayoutParams(params: LayoutParams): LayoutParams = LayoutParams(params)
}
