package com.mlevngr.inknote.backup

object BackupSchedule {
    fun isDue(lastSuccessfulAt: Long, now: Long, intervalMillis: Long): Boolean {
        require(intervalMillis > 0) { "备份间隔必须大于零" }
        return lastSuccessfulAt <= 0L || now - lastSuccessfulAt >= intervalMillis
    }
}
