package com.mlevngr.inknote.ui

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomablePdfImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    private var zoomEnabled = false
    private var zoomState = PdfPageZoom.State()
    private var onStateChanged: ((PdfPageZoom.State) -> Unit)? = null
    private var onDoubleTapAtPage: (() -> Unit)? = null
    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                updateState(
                    PdfPageZoom.scale(
                        state = zoomState,
                        factor = detector.scaleFactor,
                        focusX = detector.focusX,
                        focusY = detector.focusY,
                        viewportWidth = width.toFloat(),
                        viewportHeight = height.toFloat()
                    )
                )
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onDoubleTap(event: MotionEvent): Boolean {
                onDoubleTapAtPage?.invoke()
                return true
            }

            override fun onLongPress(event: MotionEvent) {
                performLongClick()
            }
        }
    )

    fun configurePdfZoom(
        state: PdfPageZoom.State,
        onChanged: (PdfPageZoom.State) -> Unit,
        onDoubleTap: () -> Unit
    ) {
        animate().cancel()
        zoomEnabled = true
        isClickable = true
        zoomState = state
        onStateChanged = onChanged
        onDoubleTapAtPage = onDoubleTap
        applyState(state)
    }

    fun disablePdfZoom() {
        animate().cancel()
        zoomEnabled = false
        isClickable = false
        zoomState = PdfPageZoom.reset()
        onStateChanged = null
        onDoubleTapAtPage = null
        applyState(zoomState)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!zoomEnabled) return super.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                if (PdfPageZoom.isZoomed(zoomState)) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1 &&
                    PdfPageZoom.isZoomed(zoomState)
                ) {
                    val next = PdfPageZoom.pan(
                        zoomState,
                        event.x - lastX,
                        event.y - lastY,
                        width.toFloat(),
                        height.toFloat()
                    )
                    updateState(next)
                }
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> settleAfterGesture()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (zoomEnabled) {
            zoomState = PdfPageZoom.pan(zoomState, 0f, 0f, width.toFloat(), height.toFloat())
            applyState(zoomState)
        }
    }

    private fun updateState(state: PdfPageZoom.State) {
        zoomState = state
        applyState(state)
        onStateChanged?.invoke(state)
    }

    private fun settleAfterGesture() {
        parent?.requestDisallowInterceptTouchEvent(false)
        val settled = PdfPageZoom.settle(zoomState)
        if (settled == zoomState) return
        zoomState = settled
        onStateChanged?.invoke(settled)
        animate()
            .scaleX(settled.scale)
            .scaleY(settled.scale)
            .translationX(settled.translationX)
            .translationY(settled.translationY)
            .setDuration(180L)
            .start()
    }

    private fun applyState(state: PdfPageZoom.State) {
        scaleX = state.scale
        scaleY = state.scale
        translationX = state.translationX
        translationY = state.translationY
    }
}
