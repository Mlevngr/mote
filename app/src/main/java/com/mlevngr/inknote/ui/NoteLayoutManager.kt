package com.mlevngr.inknote.ui

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Apply a background-refresh anchor only if it is still current when layout actually starts. */
class NoteLayoutManager(context: Context) : LinearLayoutManager(context) {
    private var pendingViewport: (() -> Unit)? = null

    fun keepViewport(position: Int, offset: Int, isCurrent: () -> Boolean) {
        pendingViewport = {
            if (isCurrent()) scrollToPositionWithOffset(position, offset)
        }
        requestLayout()
    }

    fun cancelViewportRestore() {
        pendingViewport = null
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (!state.isPreLayout) {
            val restore = pendingViewport
            pendingViewport = null
            restore?.invoke()
        }
        super.onLayoutChildren(recycler, state)
    }
}
