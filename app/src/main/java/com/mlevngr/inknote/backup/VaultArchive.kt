package com.mlevngr.inknote.backup

import com.mlevngr.inknote.storage.VaultRestoreTransaction
import com.mlevngr.inknote.sync.SyncPathPolicy
import com.mlevngr.inknote.storage.VaultOperationLock
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object VaultArchive {
    const val MANIFEST_PATH = "META-INF/mote-vault.properties"
    const val HASH_MANIFEST_PATH = "META-INF/mote-files.properties"
    private const val FORMAT_VERSION = "1"
    private const val HASH_ALGORITHM = "SHA-256"
    private const val MAX_ENTRY_COUNT = 100_000
    private const val MAX_EXPANDED_BYTES = 20L * 1024L * 1024L * 1024L

    fun create(vaultRoot: File, output: OutputStream, createdAt: Long = System.currentTimeMillis()) =
        VaultOperationLock.withLock {
            require(vaultRoot.isDirectory) { "笔记库不存在" }
            val canonicalRoot = vaultRoot.canonicalFile
            val fileHashes = Properties()
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_PATH))
                Properties().apply {
                    setProperty("formatVersion", FORMAT_VERSION)
                    setProperty("createdAt", createdAt.toString())
                    setProperty("hashAlgorithm", HASH_ALGORITHM)
                    setProperty("hashManifest", HASH_MANIFEST_PATH)
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
                        require(relative != MANIFEST_PATH && relative != HASH_MANIFEST_PATH) {
                            "笔记库包含备份系统保留路径：$relative"
                        }
                        val entry = ZipEntry(if (file.isDirectory) "$relative/" else relative).apply {
                            time = file.lastModified()
                        }
                        zip.putNextEntry(entry)
                        if (file.isFile) {
                            val digest = MessageDigest.getInstance(HASH_ALGORITHM)
                            file.inputStream().use { input ->
                                DigestOutputStream(zip, digest).let(input::copyTo)
                            }
                            fileHashes.setProperty(
                                relative,
                                "${file.length()}:${digest.digest().toHex()}"
                            )
                        }
                        zip.closeEntry()
                    }

                zip.putNextEntry(ZipEntry(HASH_MANIFEST_PATH))
                fileHashes.store(zip, "Mote vault file hashes")
                zip.closeEntry()
            }
        }

    fun restore(input: InputStream, vaultRoot: File) = VaultOperationLock.withLock {
        val transaction = VaultRestoreTransaction.start(vaultRoot)
        try {
            extractAndValidate(input, transaction.stagingDirectory)
            transaction.commit()
        } catch (error: Exception) {
            runCatching { VaultRestoreTransaction.recover(vaultRoot) }
                .exceptionOrNull()
                ?.let(error::addSuppressed)
            throw error
        } finally {
            transaction.discardUnprepared()
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
        properties.getProperty("hashManifest")?.let { hashManifest ->
            require(hashManifest == HASH_MANIFEST_PATH) { "备份使用了未知的文件校验清单" }
            require(properties.getProperty("hashAlgorithm") == HASH_ALGORITHM) {
                "备份使用了不支持的文件校验算法"
            }
            validateFileHashes(staging)
        }
        File(staging, "META-INF").deleteRecursively()
    }

    private fun validateFileHashes(staging: File) {
        val manifest = File(staging, HASH_MANIFEST_PATH)
        require(manifest.isFile) { "备份缺少文件校验清单" }
        val expected = Properties().apply { manifest.inputStream().use(::load) }
        val actualPaths = staging.walkTopDown()
            .filter(File::isFile)
            .map { it.relativeTo(staging).invariantSeparatorsPath }
            .filterNot { it == MANIFEST_PATH || it == HASH_MANIFEST_PATH }
            .toSet()
        require(expected.stringPropertyNames() == actualPaths) { "备份文件校验清单不完整" }
        actualPaths.forEach { path ->
            val file = SyncPathPolicy.resolve(staging, path)
            val expectedValue = expected.getProperty(path)
            val actualValue = "${file.length()}:${sha256(file)}"
            require(expectedValue == actualValue) { "备份文件校验失败：$path" }
        }
    }

    private fun isTransient(file: File): Boolean =
        file.name.endsWith(".sync.tmp") ||
            file.name.endsWith(".shared.tmp") ||
            file.name.contains(".tmp-") ||
            file.name == ".webdav-staging"

    private fun isWithin(root: File, file: File): Boolean =
        file == root || file.path.startsWith("${root.path}${File.separator}")

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
