package com.mlevngr.inknote.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

class BackupPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    val treeUri: Uri?
        get() = preferences.getString(TREE_URI, null)?.let(Uri::parse)

    val lastAttempt: BackupAttempt?
        get() {
            val attemptedAt = preferences.getLong(LAST_ATTEMPT_AT, 0L)
            if (attemptedAt <= 0L) return null
            val status = preferences.getString(LAST_ATTEMPT_STATUS, null)
                ?.let { runCatching { BackupAttempt.Status.valueOf(it) }.getOrNull() }
                ?: return null
            return BackupAttempt(
                attemptedAt = attemptedAt,
                status = status,
                fileName = preferences.getString(LAST_BACKUP_FILE_NAME, null),
                errorMessage = preferences.getString(LAST_ATTEMPT_ERROR, null)
            )
        }

    fun saveTreeUri(treeUri: Uri) {
        preferences.edit(commit = true) {
            putString(TREE_URI, treeUri.toString())
            remove(LAST_ATTEMPT_AT)
            remove(LAST_ATTEMPT_STATUS)
            remove(LAST_BACKUP_FILE_NAME)
            remove(LAST_ATTEMPT_ERROR)
        }
    }

    fun recordBackupStarted(timestamp: Long) {
        preferences.edit(commit = true) {
            putLong(LAST_ATTEMPT_AT, timestamp)
            putString(LAST_ATTEMPT_STATUS, BackupAttempt.Status.IN_PROGRESS.name)
            remove(LAST_BACKUP_FILE_NAME)
            remove(LAST_ATTEMPT_ERROR)
        }
    }

    fun recordSuccessfulBackup(timestamp: Long, fileName: String) {
        preferences.edit(commit = true) {
            putLong(LAST_ATTEMPT_AT, timestamp)
            putString(LAST_ATTEMPT_STATUS, BackupAttempt.Status.SUCCESS.name)
            putString(LAST_BACKUP_FILE_NAME, fileName)
            remove(LAST_ATTEMPT_ERROR)
        }
    }

    fun recordFailedBackup(timestamp: Long, errorMessage: String?) {
        preferences.edit(commit = true) {
            putLong(LAST_ATTEMPT_AT, timestamp)
            putString(LAST_ATTEMPT_STATUS, BackupAttempt.Status.FAILED.name)
            remove(LAST_BACKUP_FILE_NAME)
            putString(
                LAST_ATTEMPT_ERROR,
                errorMessage?.trim()?.take(MAX_ERROR_LENGTH)?.takeIf(String::isNotBlank)
            )
        }
    }

    private companion object {
        const val PREFERENCES = "vault_backup"
        const val TREE_URI = "tree_uri"
        const val LAST_ATTEMPT_AT = "last_attempt_at"
        const val LAST_ATTEMPT_STATUS = "last_attempt_status"
        const val LAST_BACKUP_FILE_NAME = "last_backup_file_name"
        const val LAST_ATTEMPT_ERROR = "last_attempt_error"
        const val MAX_ERROR_LENGTH = 200
    }
}

data class BackupAttempt(
    val attemptedAt: Long,
    val status: Status,
    val fileName: String?,
    val errorMessage: String?
) {
    enum class Status { IN_PROGRESS, SUCCESS, FAILED }
}
