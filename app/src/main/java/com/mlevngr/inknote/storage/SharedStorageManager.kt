package com.mlevngr.inknote.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.mlevngr.inknote.backup.BackupPreferences
import com.mlevngr.inknote.library.NoteLibrary
import com.mlevngr.inknote.sync.WebDavSyncEngine
import com.mlevngr.inknote.sync.WebDavSyncReport
import com.mlevngr.inknote.sync.WebDavSyncState
import java.io.File

class SharedStorageManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = SharedStoragePreferences(appContext)
    private val root = NoteLibrary(appContext).storageRoot
    private val state = WebDavSyncState(File(appContext.filesDir, STATE_FILE))

    val treeUri: Uri? get() = preferences.treeUri

    fun configure(treeUri: Uri): WebDavSyncReport = synchronized(SYNC_LOCK) {
        requireSafeSharedTree(treeUri)
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val previousTreeUri = preferences.treeUri
        appContext.contentResolver.takePersistableUriPermission(treeUri, flags)
        try {
            val report = sync(treeUri)
            preferences.save(treeUri)
            if (previousTreeUri != null && previousTreeUri != treeUri) {
                runCatching {
                    appContext.contentResolver.releasePersistableUriPermission(previousTreeUri, flags)
                }
            }
            report
        } catch (error: Exception) {
            if (previousTreeUri != treeUri) {
                runCatching { appContext.contentResolver.releasePersistableUriPermission(treeUri, flags) }
            }
            throw error
        }
    }

    fun syncIfConfigured(): WebDavSyncReport? = synchronized(SYNC_LOCK) {
        preferences.treeUri?.let(::sync)
    }

    fun displayName(): String? {
        val uri = preferences.treeUri ?: return null
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
        return appContext.contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }

    private fun sync(treeUri: Uri): WebDavSyncReport = WebDavSyncEngine(root, state).sync(
        SharedDocumentRemote(appContext.contentResolver, treeUri),
        "shared-document-tree:$treeUri"
    )

    private fun requireSafeSharedTree(candidate: Uri) {
        val backup = BackupPreferences(appContext).treeUri ?: return
        val containsBackups = runCatching {
            DocumentTreePolicy.isSameOrDescendant(
                backup.authority,
                DocumentsContract.getTreeDocumentId(backup),
                candidate.authority,
                DocumentsContract.getTreeDocumentId(candidate)
            )
        }.getOrDefault(false)
        require(!containsBackups) { "共享笔记文件夹不能包含备份目录，请选择独立目录" }
    }

    private companion object {
        const val STATE_FILE = "shared-storage-sync-state.properties"
        val SYNC_LOCK = Any()
    }
}
