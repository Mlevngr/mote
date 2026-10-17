package com.mlevngr.inknote.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.isEmpty
import kotlin.math.hypot
import kotlin.math.roundToInt

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
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var primaryPointer = MotionEvent.INVALID_POINTER_ID
    private var secondPointer = MotionEvent.INVALID_POINTER_ID
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var ownsGesture = false
    private var childHasGesture = false
    private var childDisallowsIntercept = false
    private var scrollFraction = 0f
    private val inverseTouchMatrix = Matrix()
    private val inverseTouchValues = FloatArray(9).apply { this[8] = 1f }

    val currentScale: Float get() = transform.scale

    /** Called once when a drag/pinch takes over, so stale focus work can be cancelled. */
    var onUserNavigation: (() -> Unit)? = null
    var onScrollContent: ((Int) -> Unit)? = null

    var onScaleChanged: ((Float) -> Unit)? = null

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
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        transform = NoteCanvasZoom.constrain(transform, width.toFloat(), height.toFloat())
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val checkpoint = canvas.save()
        canvas.translate(transform.translationX, transform.translationY)
        canvas.scale(transform.scale, transform.scale)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(checkpoint)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        childDisallowsIntercept = disallowIntercept
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Gesture recognition always uses viewport coordinates, never inverse-mapped child coordinates.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            reboundAnimator?.cancel()
            reboundAnimator = null
            ownsGesture = false
            pinching = false
            childDisallowsIntercept = false
            scrollFraction = 0f
            primaryPointer = event.getPointerId(0)
            downX = event.x
            downY = event.y
            lastX = downX
            lastY = downY
            childHasGesture = true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                takeGesture(event)
                if (!pinching) {
                    primaryPointer = event.getPointerId(0)
                    secondPointer = event.getPointerId(1)
                    beginPinch(event)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinching) {
                    updatePinch(event)
                    return true
                }
                val index = event.findPointerIndex(primaryPointer)
                if (index >= 0) {
                    val x = event.getX(index)
                    val y = event.getY(index)
                    if (!ownsGesture && transform.scale > 1f && !childDisallowsIntercept &&
                        hypot(x - downX, y - downY) > touchSlop
                    ) {
                        takeGesture(event)
                    }
                    if (ownsGesture) {
                        pan(x - lastX, y - lastY)
                        lastX = x
                        lastY = y
                        return true
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> if (ownsGesture) {
                val remaining = (0 until event.pointerCount).filter { it != event.actionIndex }
                // Rebase on pointer IDs: index 0 can be the pointer that just left.
                if (remaining.size >= 2) {
                    primaryPointer = event.getPointerId(remaining[0])
                    secondPointer = event.getPointerId(remaining[1])
                    beginPinch(event)
                } else {
                    finishPinch()
                    secondPointer = MotionEvent.INVALID_POINTER_ID
                    remaining.firstOrNull()?.let { index ->
                        primaryPointer = event.getPointerId(index)
                        lastX = event.getX(index)
                        lastY = event.getY(index)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val consumed = ownsGesture
                finishPinch()
                ownsGesture = false
                primaryPointer = MotionEvent.INVALID_POINTER_ID
                secondPointer = MotionEvent.INVALID_POINTER_ID
                if (consumed) {
                    // No click after a pinch or pan, including cancellation.
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                childHasGesture = false
            }
        }
        val handled = dispatchToContent(event)
        return handled || (transform.scale > 1f && event.actionMasked == MotionEvent.ACTION_DOWN)
    }

    private fun takeGesture(event: MotionEvent) {
        if (ownsGesture) return
        ownsGesture = true
        if (childHasGesture) {
            val cancel = MotionEvent.obtain(event)
            cancel.action = MotionEvent.ACTION_CANCEL
            dispatchToContent(cancel)
            cancel.recycle()
            childHasGesture = false
        }
        parent?.requestDisallowInterceptTouchEvent(true)
        onUserNavigation?.invoke()
    }

    private fun dispatchToContent(event: MotionEvent): Boolean {
        if (transform == NoteCanvasZoom.Transform()) return super.dispatchTouchEvent(event)
        val mapped = MotionEvent.obtain(event)
        val inverseScale = 1f / transform.scale
        inverseTouchValues[0] = inverseScale
        inverseTouchValues[2] = -transform.translationX * inverseScale
        inverseTouchValues[4] = inverseScale
        inverseTouchValues[5] = -transform.translationY * inverseScale
        inverseTouchMatrix.setValues(inverseTouchValues)
        mapped.transform(inverseTouchMatrix)
        return try {
            super.dispatchTouchEvent(mapped)
        } finally {
            mapped.recycle()
        }
    }

    private fun pan(deltaX: Float, deltaY: Float) {
        val result = NoteCanvasZoom.pan(transform, deltaX, deltaY, width.toFloat(), height.toFloat())
        setTransform(result.transform)
        val scroll = result.scrollY + scrollFraction
        val pixels = scroll.roundToInt()
        scrollFraction = scroll - pixels
        if (pixels != 0) onScrollContent?.invoke(pixels)
    }

    /** Top of the actually visible canvas in a descendant's unscaled coordinates. */
    fun visibleTopIn(descendant: View): Float {
        val bounds = Rect(0, 0, descendant.width, descendant.height)
        offsetDescendantRectToMyCoords(descendant, bounds)
        return -transform.translationY / transform.scale - bounds.top
    }

    /** Reveal only the caret, taking the custom drawing transform into account. */
    fun revealDescendantRect(descendant: View, rectangle: Rect) {
        val contentRect = Rect(rectangle)
        offsetDescendantRectToMyCoords(descendant, contentRect)
        val left = contentRect.left * transform.scale + transform.translationX
        val right = contentRect.right * transform.scale + transform.translationX
        val top = contentRect.top * transform.scale + transform.translationY
        val bottom = contentRect.bottom * transform.scale + transform.translationY
        val dx = if (left < 0f) -left else if (right > width) width - right else 0f
        val dy = if (top < 0f) -top else if (bottom > height) height - bottom else 0f
        pan(dx, dy)
    }

    override fun onDetachedFromWindow() {
        reboundAnimator?.cancel()
        reboundAnimator = null
        super.onDetachedFromWindow()
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
        val scaleChanged = value.scale != transform.scale
        transform = value
        invalidate()
        if (scaleChanged) onScaleChanged?.invoke(value.scale)
    }

    private fun pointerIndices(event: MotionEvent): Pair<Int, Int>? {
        val first = event.findPointerIndex(primaryPointer)
        val second = event.findPointerIndex(secondPointer)
        return if (first >= 0 && second >= 0) first to second else null
    }

    private fun span(event: MotionEvent): Float {
        val (first, second) = pointerIndices(event) ?: return 0f
        return hypot(event.getX(first) - event.getX(second), event.getY(first) - event.getY(second))
    }

    private fun focusX(event: MotionEvent): Float {
        val (first, second) = pointerIndices(event) ?: return lastFocusX
        return (event.getX(first) + event.getX(second)) / 2f
    }

    private fun focusY(event: MotionEvent): Float {
        val (first, second) = pointerIndices(event) ?: return lastFocusY
        return (event.getY(first) + event.getY(second)) / 2f
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

    override fun generateLayoutParams(attrs: AttributeSet): LayoutParams =
        LayoutParams(context, attrs)

    override fun generateLayoutParams(params: LayoutParams): LayoutParams = LayoutParams(params)
}
