package com.mlevngr.inknote.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

class BackupPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    val treeUri: Uri?
        get() = preferences.getString(TREE_URI, null)?.let(Uri::parse)

    val lastSuccessfulBackupAt: Long
        get() = preferences.getLong(LAST_SUCCESSFUL_BACKUP_AT, 0L)

    fun saveTreeUri(treeUri: Uri) {
        preferences.edit {
            putString(TREE_URI, treeUri.toString())
            putLong(LAST_SUCCESSFUL_BACKUP_AT, 0L)
        }
    }

    fun recordSuccessfulBackup(timestamp: Long) {
        preferences.edit { putLong(LAST_SUCCESSFUL_BACKUP_AT, timestamp) }
    }

    private companion object {
        const val PREFERENCES = "vault_backup"
        const val TREE_URI = "tree_uri"
        const val LAST_SUCCESSFUL_BACKUP_AT = "last_successful_backup_at"
    }
}
