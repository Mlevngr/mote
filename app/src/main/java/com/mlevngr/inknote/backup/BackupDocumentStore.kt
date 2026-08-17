package com.mlevngr.inknote.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class BackupDocumentStore(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
    private val retention: BackupRetentionPolicy = BackupRetentionPolicy()
) {
    private val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)

    fun ensureAccessible() {
        requireNotNull(queryDocument(documentUri(rootDocumentId))) { "无法访问所选备份目录" }
    }

    fun displayName(): String? = queryDocument(documentUri(rootDocumentId))?.name

    fun store(archive: File, timestamp: Long): StoredBackup {
        require(archive.isFile) { "备份暂存文件不存在" }
        cleanupPartialFiles()
        val finalName = uniqueBackupName(timestamp)
        val partialName = "$finalName.partial"
        val partialUri = DocumentsContract.createDocument(
            resolver,
            documentUri(rootDocumentId),
            "application/octet-stream",
            partialName
        ) ?: error("无法在所选目录创建备份")
        try {
            val output = runCatching { resolver.openOutputStream(partialUri, "rwt") }.getOrNull()
                ?: resolver.openOutputStream(partialUri, "wt")
                ?: error("无法写入备份")
            output.use { stream -> archive.inputStream().use { it.copyTo(stream) } }
            check(sha256(archive) == sha256(partialUri)) { "备份写入校验失败" }
            val finalUri = DocumentsContract.renameDocument(resolver, partialUri, finalName)
                ?: error("文件提供器不支持安全完成备份")
            runCatching { cleanupExpired() }
            return StoredBackup(finalName, timestamp, finalUri)
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, partialUri) }
            throw error
        }
    }

    fun cleanupExpired(): Int {
        val backups = listBackups()
        val expired = retention.expired(backups.map { BackupRecord(it.name, it.modifiedAt) })
            .map(BackupRecord::name)
            .toSet()
        return backups.count { backup ->
            backup.name in expired && runCatching {
                DocumentsContract.deleteDocument(resolver, backup.uri)
            }.getOrDefault(false)
        }
    }

    fun backupCount(): Int = listBackups().size

    private fun listBackups(): List<DocumentNode> = queryChildren()
        .filter { !it.isDirectory && BACKUP_NAME.matches(it.name) }

    private fun cleanupPartialFiles() {
        queryChildren()
            .filter { !it.isDirectory && it.name.startsWith(BACKUP_PREFIX) && it.name.endsWith(".partial") }
            .forEach { runCatching { DocumentsContract.deleteDocument(resolver, it.uri) } }
    }

    private fun uniqueBackupName(timestamp: Long): String {
        val formatted = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.ROOT).format(Date(timestamp))
        val base = "$BACKUP_PREFIX$formatted"
        val existing = queryChildren().map(DocumentNode::name).toSet()
        var name = "$base.zip"
        var counter = 2
        while (name in existing || "$name.partial" in existing) {
            name = "$base-$counter.zip"
            counter++
        }
        return name
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

    private fun queryChildren(): List<DocumentNode> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocumentId)
        return resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(0)
                    add(
                        DocumentNode(
                            uri = documentUri(documentId),
                            name = cursor.getString(1) ?: continue,
                            mimeType = cursor.getString(2).orEmpty(),
                            modifiedAt = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                        )
                    )
                }
            }
        }.orEmpty()
    }

    private fun queryDocument(uri: Uri): DocumentNode? =
        resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null else DocumentNode(
                uri = uri,
                name = cursor.getString(1) ?: return@use null,
                mimeType = cursor.getString(2).orEmpty(),
                modifiedAt = if (cursor.isNull(3)) 0L else cursor.getLong(3)
            )
        }

    private fun documentUri(documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    data class StoredBackup(val name: String, val modifiedAt: Long, val uri: Uri)

    private data class DocumentNode(
        val uri: Uri,
        val name: String,
        val mimeType: String,
        val modifiedAt: Long
    ) {
        val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }

    private companion object {
        const val BACKUP_PREFIX = "Mote-backup-"
        val BACKUP_NAME = Regex("^Mote-backup-\\d{4}-\\d{2}-\\d{2}_\\d{6}(?:-\\d+)?\\.zip$")
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
    }
}
