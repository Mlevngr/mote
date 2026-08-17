package com.mlevngr.inknote.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.mlevngr.inknote.sync.RemoteFile
import com.mlevngr.inknote.sync.SyncPathPolicy
import com.mlevngr.inknote.sync.WebDavEndpoint
import com.mlevngr.inknote.sync.WebDavRemote
import java.io.File
import java.net.URLConnection
import java.security.MessageDigest
import java.util.ArrayDeque

/** SAF document-tree transport used by the existing conflict-safe synchronization engine. */
internal class SharedDocumentRemote(
    private val resolver: ContentResolver,
    private val treeUri: Uri
) : WebDavRemote {
    override val endpoint = WebDavEndpoint(WebDavEndpoint.Kind.Internal, treeUri.toString())
    private val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
    private val nodesByPath = mutableMapOf<String, DocumentNode>()

    override fun ensureRoot() {
        queryDocument(rootDocumentId)
            ?: throw IllegalStateException("无法访问所选共享文件夹，请重新选择")
    }

    override fun listFiles(): List<RemoteFile> {
        nodesByPath.clear()
        val files = mutableListOf<RemoteFile>()
        val pending = ArrayDeque<Pair<String, String>>()
        pending += rootDocumentId to ""
        while (pending.isNotEmpty()) {
            val (parentId, prefix) = pending.removeFirst()
            queryChildren(parentId).forEach { node ->
                if (!isSafeName(node.name)) return@forEach
                val path = if (prefix.isEmpty()) node.name else "$prefix/${node.name}"
                nodesByPath[path] = node
                if (node.isDirectory) {
                    pending += node.documentId to path
                } else {
                    files += remoteFile(path, node)
                }
            }
        }
        return files
    }

    override fun download(path: String, destination: File) {
        val normalized = SyncPathPolicy.normalize(path)
        val node = findNode(normalized) ?: error("共享文件不存在：$normalized")
        require(!node.isDirectory) { "共享路径不是文件：$normalized" }
        val temporary = File(destination.parentFile, "${destination.name}.shared.tmp")
        temporary.parentFile?.mkdirs()
        requireNotNull(resolver.openInputStream(node.uri)).use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        }
        destination.parentFile?.mkdirs()
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
    }

    override fun upload(path: String, source: File): RemoteFile {
        val normalized = SyncPathPolicy.normalize(path)
        val segments = normalized.split('/')
        val parent = ensureDirectories(segments.dropLast(1))
        val existing = findChild(parent.documentId, segments.last())
        require(existing == null || !existing.isDirectory) { "共享目录中存在同名文件夹" }
        val target = existing ?: createDocument(
            parent.documentId,
            mimeType(segments.last()),
            segments.last()
        )
        val output = runCatching { resolver.openOutputStream(target.uri, "rwt") }.getOrNull()
            ?: resolver.openOutputStream(target.uri, "wt")
            ?: error("无法写入共享文件：$normalized")
        output.use { stream -> source.inputStream().use { it.copyTo(stream) } }
        val updated = queryDocument(target.documentId) ?: target
        nodesByPath[normalized] = updated
        return remoteFile(normalized, updated)
    }

    override fun delete(path: String) {
        val normalized = SyncPathPolicy.normalize(path)
        val node = findNode(normalized) ?: return
        check(DocumentsContract.deleteDocument(resolver, node.uri)) {
            "无法删除共享文件：$normalized"
        }
        nodesByPath.remove(normalized)
        pruneEmptyParents(normalized.substringBeforeLast('/', ""))
    }

    private fun ensureDirectories(segments: List<String>): DocumentNode {
        var current = requireNotNull(queryDocument(rootDocumentId))
        var path = ""
        segments.forEach { segment ->
            require(isSafeName(segment)) { "共享目录名称无效" }
            path = if (path.isEmpty()) segment else "$path/$segment"
            val child = nodesByPath[path] ?: findChild(current.documentId, segment)
            current = when {
                child == null -> createDocument(
                    current.documentId,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    segment
                )
                child.isDirectory -> child
                else -> error("共享目录路径被同名文件占用：$path")
            }
            nodesByPath[path] = current
        }
        return current
    }

    private fun findNode(path: String): DocumentNode? {
        nodesByPath[path]?.let { return it }
        var current = requireNotNull(queryDocument(rootDocumentId))
        var currentPath = ""
        path.split('/').forEach { segment ->
            currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
            current = findChild(current.documentId, segment) ?: return null
            nodesByPath[currentPath] = current
        }
        return current
    }

    private fun findChild(parentId: String, name: String): DocumentNode? =
        queryChildren(parentId).firstOrNull { it.name == name }

    private fun pruneEmptyParents(startPath: String) {
        var path = startPath
        while (path.isNotEmpty()) {
            val node = findNode(path) ?: return
            if (!node.isDirectory || queryChildren(node.documentId).isNotEmpty()) return
            if (!DocumentsContract.deleteDocument(resolver, node.uri)) return
            nodesByPath.remove(path)
            path = path.substringBeforeLast('/', "")
        }
    }

    private fun createDocument(parentId: String, mimeType: String, name: String): DocumentNode {
        val parentUri = documentUri(parentId)
        val created = DocumentsContract.createDocument(resolver, parentUri, mimeType, name)
            ?: error("无法在共享目录创建：$name")
        val documentId = DocumentsContract.getDocumentId(created)
        return queryDocument(documentId) ?: DocumentNode(documentId, created, name, mimeType, 0, 0)
    }

    private fun queryChildren(parentId: String): List<DocumentNode> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        return resolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        DocumentNode(
                            documentId = cursor.getString(0),
                            uri = documentUri(cursor.getString(0)),
                            name = cursor.getString(1),
                            mimeType = cursor.getString(2),
                            modifiedAt = cursor.getLong(3),
                            size = cursor.getLong(4)
                        )
                    )
                }
            }
        }.orEmpty()
    }

    private fun queryDocument(documentId: String): DocumentNode? =
        resolver.query(documentUri(documentId), PROJECTION, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null else DocumentNode(
                documentId = cursor.getString(0),
                uri = documentUri(cursor.getString(0)),
                name = cursor.getString(1),
                mimeType = cursor.getString(2),
                modifiedAt = cursor.getLong(3),
                size = cursor.getLong(4)
            )
        }

    private fun documentUri(documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "md" -> "text/markdown"
        "json" -> "application/json"
        "pdf" -> "application/pdf"
        else -> URLConnection.guessContentTypeFromName(name) ?: "application/octet-stream"
    }

    private fun remoteFile(path: String, node: DocumentNode): RemoteFile = RemoteFile(
        path = path,
        version = if (shouldHash(path, node.size)) {
            "${node.version}:${contentHash(node.uri)}"
        } else {
            node.version
        },
        modifiedAt = node.modifiedAt,
        size = node.size
    )

    private fun shouldHash(path: String, size: Long): Boolean =
        size in 0..MAX_HASHED_FILE_SIZE && (
            path.endsWith(".md", ignoreCase = true) ||
                path.endsWith(".properties", ignoreCase = true) ||
                path.substringAfterLast('/').startsWith('.')
            )

    private fun contentHash(uri: Uri): String = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        requireNotNull(resolver.openInputStream(uri)).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault("unavailable")

    private fun isSafeName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\u0000' !in name

    private data class DocumentNode(
        val documentId: String,
        val uri: Uri,
        val name: String,
        val mimeType: String,
        val modifiedAt: Long,
        val size: Long
    ) {
        val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
        val version: String get() = "$documentId:$modifiedAt:$size"
    }

    private companion object {
        const val MAX_HASHED_FILE_SIZE = 1024L * 1024L
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE
        )
    }
}
