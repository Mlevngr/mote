package com.mlevngr.inknote.sync

import com.mlevngr.inknote.storage.VaultOperationLock
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WebDavSyncEngine(
    private val root: File,
    private val state: WebDavSyncState,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val conflictNotes = mutableMapOf<String, File>()

    fun sync(transport: WebDavRemote, identity: String): WebDavSyncReport =
        VaultOperationLock.withLock { syncUnlocked(transport, identity) }

    private fun syncUnlocked(transport: WebDavRemote, identity: String): WebDavSyncReport {
        root.mkdirs()
        conflictNotes.clear()
        ensureFolderMarkers()
        transport.ensureRoot()
        val remote = transport.listFiles().associateBy(RemoteFile::path)
        val local = localFiles()
        val previous = state.load(identity)
        val updated = mutableMapOf<String, WebDavSyncRecord>()
        var uploaded = 0
        var downloaded = 0
        var deletedLocal = 0
        var deletedRemote = 0
        var conflicts = 0

        (local.keys + remote.keys + previous.keys).toSortedSet().forEach { path ->
            val localFile = local[path]
            val remoteFile = remote[path]
            val record = previous[path]
            when {
                localFile != null && remoteFile != null -> {
                    val localHash = sha256(localFile)
                    val localChanged = record != null && record.localHash != localHash
                    val remoteChanged = record != null && record.remoteVersion != remoteFile.version
                    when {
                        record == null -> {
                            val remoteCopy = downloadTemporary(transport, path)
                            val remoteHash = sha256(remoteCopy)
                            if (remoteHash != localHash) {
                                preserveConflict(path, remoteCopy)
                                conflicts++
                                val uploadedFile = transport.upload(path, localFile)
                                uploaded++
                                updated[path] = WebDavSyncRecord(localHash, uploadedFile.version)
                            } else {
                                remoteCopy.delete()
                                updated[path] = WebDavSyncRecord(localHash, remoteFile.version)
                            }
                        }
                        localChanged && remoteChanged -> {
                            preserveConflict(path, downloadTemporary(transport, path))
                            conflicts++
                            val uploadedFile = transport.upload(path, localFile)
                            uploaded++
                            updated[path] = WebDavSyncRecord(localHash, uploadedFile.version)
                        }
                        remoteChanged -> {
                            transport.download(path, localFile)
                            downloaded++
                            updated[path] = WebDavSyncRecord(sha256(localFile), remoteFile.version)
                        }
                        localChanged -> {
                            val uploadedFile = transport.upload(path, localFile)
                            uploaded++
                            updated[path] = WebDavSyncRecord(localHash, uploadedFile.version)
                        }
                        else -> updated[path] = requireNotNull(record)
                    }
                }
                localFile != null && record != null -> {
                    val localHash = sha256(localFile)
                    if (localHash == record.localHash) {
                        if (localFile.delete()) {
                            deletedLocal++
                            pruneEmptyParents(localFile.parentFile)
                        }
                    } else {
                        val uploadedFile = transport.upload(path, localFile)
                        uploaded++
                        conflicts++
                        updated[path] = WebDavSyncRecord(localHash, uploadedFile.version)
                    }
                }
                localFile != null -> {
                    val localHash = sha256(localFile)
                    val uploadedFile = transport.upload(path, localFile)
                    uploaded++
                    updated[path] = WebDavSyncRecord(localHash, uploadedFile.version)
                }
                remoteFile != null && record != null -> {
                    if (remoteFile.version == record.remoteVersion) {
                        transport.delete(path)
                        deletedRemote++
                    } else {
                        preserveConflict(path, downloadTemporary(transport, path))
                        conflicts++
                        transport.delete(path)
                        deletedRemote++
                    }
                }
                remoteFile != null -> {
                    val destination = SyncPathPolicy.resolve(root, path)
                    transport.download(path, destination)
                    downloaded++
                    updated[path] = WebDavSyncRecord(sha256(destination), remoteFile.version)
                }
            }
        }
        localFiles()
            .filterKeys { it !in local && it !in updated }
            .forEach { (path, file) ->
                val uploadedFile = transport.upload(path, file)
                uploaded++
                updated[path] = WebDavSyncRecord(sha256(file), uploadedFile.version)
            }
        state.save(identity, updated)
        return WebDavSyncReport(
            transport.endpoint,
            uploaded,
            downloaded,
            deletedLocal,
            deletedRemote,
            conflicts
        )
    }

    private fun localFiles(): Map<String, File> = root.walkTopDown()
        .filter(File::isFile)
        .filterNot { it.name.endsWith(".tmp") || it.name.endsWith(".sync.tmp") }
        .associateBy { it.relativeTo(root).invariantSeparatorsPath }

    private fun ensureFolderMarkers() {
        root.walkTopDown()
            .filter(File::isDirectory)
            .filter { it != root && isLibraryFolder(it) }
            .forEach { folder ->
                File(folder, FOLDER_MARKER).apply {
                    if (!exists()) writeText("")
                }
            }
    }

    private fun isLibraryFolder(directory: File): Boolean {
        var current: File? = directory
        while (current != null && current != root) {
            if (current.name.startsWith('.') || File(current, NOTE_FILE).isFile) return false
            current = current.parentFile
        }
        return current == root
    }

    private fun downloadTemporary(transport: WebDavRemote, path: String): File {
        val staging = File(root.parentFile, SYNC_STAGING).apply { mkdirs() }
        val temporary = File.createTempFile("remote-", ".tmp", staging)
        transport.download(path, temporary)
        return temporary
    }

    private fun preserveConflict(path: String, remoteCopy: File) {
        val segments = SyncPathPolicy.normalize(path).split('/')
        val noteIndex = segments.indexOfFirst { it.endsWith(NOTE_SUFFIX) }
        val destination = if (noteIndex >= 0) {
            val notePath = segments.take(noteIndex + 1).joinToString("/")
            val originalNote = File(root, notePath.replace('/', File.separatorChar))
            val conflictNote = conflictNotes.getOrPut(notePath) {
                uniqueConflict(
                    File(root, segments.take(noteIndex).joinToString(File.separator)),
                    originalNote.name.removeSuffix(NOTE_SUFFIX),
                    NOTE_SUFFIX
                ).also { conflict ->
                    if (originalNote.isDirectory) originalNote.copyRecursively(conflict, overwrite = false)
                }
            }
            File(conflictNote, segments.drop(noteIndex + 1).joinToString(File.separator))
        } else {
            val original = SyncPathPolicy.resolve(root, path)
            uniqueConflict(requireNotNull(original.parentFile), original.nameWithoutExtension,
                original.extension.takeIf(String::isNotEmpty)?.let { ".$it" }.orEmpty())
        }
        destination.parentFile?.mkdirs()
        remoteCopy.copyTo(destination, overwrite = true)
        remoteCopy.delete()
    }

    private fun uniqueConflict(parent: File, baseName: String, suffix: String): File {
        parent.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(now()))
        var candidate = File(parent, "$baseName (同步冲突 $stamp)$suffix")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(parent, "$baseName (同步冲突 $stamp-$counter)$suffix")
            counter++
        }
        return candidate
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun pruneEmptyParents(start: File?) {
        var directory = start
        while (directory != null && directory != root && directory.list().isNullOrEmpty()) {
            if (!directory.delete()) return
            directory = directory.parentFile
        }
    }

    private companion object {
        const val SYNC_STAGING = ".webdav-staging"
        const val NOTE_SUFFIX = ".note"
        const val NOTE_FILE = "note.md"
        const val FOLDER_MARKER = ".mote-folder"
    }
}
