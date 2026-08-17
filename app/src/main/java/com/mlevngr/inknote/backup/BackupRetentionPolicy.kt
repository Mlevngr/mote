package com.mlevngr.inknote.backup

data class BackupRecord(val name: String, val modifiedAt: Long)

class BackupRetentionPolicy(private val keepCount: Int = DEFAULT_KEEP_COUNT) {
    init {
        require(keepCount >= 1) { "至少保留一个备份" }
    }

    fun expired(backups: List<BackupRecord>): List<BackupRecord> = backups
        .sortedWith(compareByDescending<BackupRecord> { it.modifiedAt }.thenByDescending { it.name })
        .drop(keepCount)
        .sortedWith(compareBy<BackupRecord> { it.modifiedAt }.thenBy { it.name })

    companion object {
        const val DEFAULT_KEEP_COUNT = 14
    }
}
