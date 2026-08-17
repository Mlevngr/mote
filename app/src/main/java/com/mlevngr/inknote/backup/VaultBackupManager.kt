package com.mlevngr.inknote.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.mlevngr.inknote.library.NoteLibrary
import com.mlevngr.inknote.storage.DocumentTreePolicy
import com.mlevngr.inknote.storage.SharedStoragePreferences
import java.io.File
import java.security.MessageDigest

class VaultBackupManager(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val preferences = BackupPreferences(appContext)
    private val vaultRoot = NoteLibrary(appContext).storageRoot

    val backupTreeUri: Uri? get() = preferences.treeUri
    val lastSuccessfulBackupAt: Long get() = preferences.lastSuccessfulBackupAt

    fun configureBackupFolder(treeUri: Uri) = synchronized(BACKUP_LOCK) {
        requireSafeBackupTree(treeUri)
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val previous = preferences.treeUri
        resolver.takePersistableUriPermission(treeUri, flags)
        try {
            BackupDocumentStore(resolver, treeUri).ensureAccessible()
            preferences.saveTreeUri(treeUri)
            if (previous != null && previous != treeUri) {
                runCatching { resolver.releasePersistableUriPermission(previous, flags) }
            }
        } catch (error: Exception) {
            if (previous != treeUri) {
                runCatching { resolver.releasePersistableUriPermission(treeUri, flags) }
            }
            throw error
        }
    }

    fun backupFolderDisplayName(): String? = preferences.treeUri?.let { treeUri ->
        BackupDocumentStore(resolver, treeUri).displayName()
    }

    fun createBackup(now: Long = System.currentTimeMillis()): BackupResult = synchronized(BACKUP_LOCK) {
        val treeUri = preferences.treeUri ?: error("请先选择自动备份文件夹")
        requireSafeBackupTree(treeUri)
        val temporary = createArchiveFile(now)
        try {
            val store = BackupDocumentStore(resolver, treeUri)
            val backup = store.store(temporary, now)
            preferences.recordSuccessfulBackup(now)
            BackupResult(backup.name, runCatching { store.backupCount() }.getOrDefault(1))
        } finally {
            temporary.delete()
        }
    }

    fun runAutomaticBackupIfDue(now: Long = System.currentTimeMillis()): BackupResult? {
        if (preferences.treeUri == null) return null
        if (!BackupSchedule.isDue(
                preferences.lastSuccessfulBackupAt,
                now,
                AUTOMATIC_INTERVAL_MILLIS
            )) return null
        return createBackup(now)
    }

    fun cleanupOldBackups(): Int = synchronized(BACKUP_LOCK) {
        val treeUri = preferences.treeUri ?: error("请先选择自动备份文件夹")
        BackupDocumentStore(resolver, treeUri).cleanupExpired()
    }

    fun exportVault(destination: Uri, now: Long = System.currentTimeMillis()) = synchronized(BACKUP_LOCK) {
        val temporary = createArchiveFile(now)
        try {
            val output = runCatching { resolver.openOutputStream(destination, "rwt") }.getOrNull()
                ?: resolver.openOutputStream(destination, "wt")
                ?: error("无法写入导出文件")
            output.use { stream -> temporary.inputStream().use { it.copyTo(stream) } }
            check(sha256(temporary) == sha256(destination)) { "导出文件校验失败" }
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, destination) }
            throw error
        } finally {
            temporary.delete()
        }
    }

    fun restoreVault(source: Uri) = synchronized(BACKUP_LOCK) {
        requireNotNull(resolver.openInputStream(source)).use { input ->
            VaultArchive.restore(input, vaultRoot)
        }
    }

    private fun createArchiveFile(now: Long): File {
        val directory = File(appContext.cacheDir, "vault-archives").also(File::mkdirs)
        return File.createTempFile("Mote-", ".zip.part", directory).also { temporary ->
            try {
                temporary.outputStream().use { VaultArchive.create(vaultRoot, it, now) }
            } catch (error: Exception) {
                temporary.delete()
                throw error
            }
        }
    }

    private fun requireSafeBackupTree(candidate: Uri) {
        val shared = SharedStoragePreferences(appContext).treeUri ?: return
        val nested = runCatching {
            DocumentTreePolicy.isSameOrDescendant(
                candidate.authority,
                DocumentsContract.getTreeDocumentId(candidate),
                shared.authority,
                DocumentsContract.getTreeDocumentId(shared)
            )
        }.getOrDefault(false)
        require(!nested) { "备份文件夹不能位于共享笔记文件夹内部，请选择独立目录" }
    }

    private fun sha256(file: File): String = file.inputStream().use(::sha256)

    private fun sha256(uri: Uri): String = requireNotNull(resolver.openInputStream(uri)).use(::sha256)

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    data class BackupResult(val fileName: String, val retainedCount: Int)

    companion object {
        const val AUTOMATIC_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
        private val BACKUP_LOCK = Any()
    }
}
