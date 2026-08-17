package com.mlevngr.inknote.storage

object VaultOperationLock {
    private val monitor = Any()

    fun <T> withLock(operation: () -> T): T = synchronized(monitor) { operation() }
}
