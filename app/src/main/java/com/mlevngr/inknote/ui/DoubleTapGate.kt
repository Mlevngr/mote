package com.mlevngr.inknote.ui

internal class DoubleTapGate(private val timeoutMillis: Long) {
    private var firstTapAt: Long? = null

    fun registerTap(nowMillis: Long): Boolean {
        val previous = firstTapAt
        if (previous != null && nowMillis >= previous && nowMillis - previous <= timeoutMillis) {
            firstTapAt = null
            return true
        }
        firstTapAt = nowMillis
        return false
    }
}
