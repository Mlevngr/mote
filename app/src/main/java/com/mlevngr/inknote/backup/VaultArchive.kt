package com.mlevngr.inknote.backup

import com.mlevngr.inknote.sync.SyncPathPolicy
import com.mlevngr.inknote.storage.VaultOperationLock
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object VaultArchive {
    const val MANIFEST_PATH = "META-INF/mote-vault.properties"
    private const val FORMAT_VERSION = "1"
    private const val MAX_ENTRY_COUNT = 100_000
    private const val MAX_EXPANDED_BYTES = 20L * 1024L * 1024L * 1024L

    fun create(vaultRoot: File, output: OutputStream, createdAt: Long = System.currentTimeMillis()) =
        VaultOperationLock.withLock {
            require(vaultRoot.isDirectory) { "笔记库不存在" }
            val canonicalRoot = vaultRoot.canonicalFile
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_PATH))
                Properties().apply {
                    setProperty("formatVersion", FORMAT_VERSION)
                    setProperty("createdAt", createdAt.toString())
                }.store(zip, "Mote vault archive")
                zip.closeEntry()

                canonicalRoot.walkTopDown()
                    .onEnter { directory -> !Files.isSymbolicLink(directory.toPath()) }
                    .drop(1)
                    .filterNot(::isTransient)
                    .sortedBy { it.relativeTo(canonicalRoot).invariantSeparatorsPath }
                    .forEach { file ->
                        require(!Files.isSymbolicLink(file.toPath())) { "备份不支持符号链接" }
                        require(isWithin(canonicalRoot, file.canonicalFile)) { "文件越过笔记库目录" }
                        val relative = file.relativeTo(canonicalRoot).invariantSeparatorsPath
                        val entry = ZipEntry(if (file.isDirectory) "$relative/" else relative).apply {
                            time = file.lastModified()
                        }
                        zip.putNextEntry(entry)
                        if (file.isFile) file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
            }
        }

    fun restore(input: InputStream, vaultRoot: File) = VaultOperationLock.withLock {
        val parent = requireNotNull(vaultRoot.parentFile) { "笔记库缺少父目录" }
        check(parent.exists() || parent.mkdirs()) { "无法创建笔记库目录" }
        val staging = File(parent, ".mote-restore-${UUID.randomUUID()}")
        val rollback = File(parent, ".mote-rollback-${UUID.randomUUID()}")
        check(staging.mkdir()) { "无法创建恢复暂存目录" }
        try {
            extractAndValidate(input, staging)
            val hadExistingVault = vaultRoot.exists()
            if (hadExistingVault) move(vaultRoot, rollback)
            try {
                move(staging, vaultRoot)
            } catch (error: Exception) {
                if (hadExistingVault && rollback.exists() && !vaultRoot.exists()) {
                    runCatching { move(rollback, vaultRoot) }
                }
                throw error
            }
            rollback.deleteRecursively()
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun extractAndValidate(input: InputStream, staging: File) {
        var entryCount = 0
        var expandedBytes = 0L
        var manifestFound = false
        val paths = mutableSetOf<String>()
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ENTRY_COUNT) { "备份包含过多文件" }
                val path = entry.name.replace('\\', '/').trimEnd('/')
                require(path.isNotBlank()) { "备份包含无效路径" }
                require(paths.add(path)) { "备份包含重复路径：$path" }
                val destination = SyncPathPolicy.resolve(staging, path)
                if (entry.isDirectory) {
                    check(destination.mkdirs() || destination.isDirectory) { "无法恢复目录：$path" }
                } else {
                    destination.parentFile?.mkdirs()
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            expandedBytes += count
                            require(expandedBytes <= MAX_EXPANDED_BYTES) { "备份解压后过大" }
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                    if (entry.time > 0) destination.setLastModified(entry.time)
                }
                zip.closeEntry()
                if (path == MANIFEST_PATH) manifestFound = true
            }
        }
        require(entryCount > 0 && manifestFound) { "不是有效的 Mote 备份" }
        val manifest = File(staging, MANIFEST_PATH)
        val properties = Properties().apply { manifest.inputStream().use(::load) }
        require(properties.getProperty("formatVersion") == FORMAT_VERSION) {
            "不支持此 Mote 备份版本"
        }
        File(staging, "META-INF").deleteRecursively()
    }

    private fun isTransient(file: File): Boolean =
        file.name.endsWith(".sync.tmp") ||
            file.name.endsWith(".shared.tmp") ||
            file.name.contains(".tmp-") ||
            file.name == ".webdav-staging"

    private fun isWithin(root: File, file: File): Boolean =
        file == root || file.path.startsWith("${root.path}${File.separator}")

    private fun move(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath())
        }
    }
}
