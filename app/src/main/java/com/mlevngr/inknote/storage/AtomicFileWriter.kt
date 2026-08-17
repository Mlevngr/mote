package com.mlevngr.inknote.storage

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object AtomicFileWriter {
    fun writeText(target: File, value: String) = writeBytes(target, value.toByteArray(Charsets.UTF_8))

    fun writeBytes(target: File, value: ByteArray) = write(target) { it.write(value) }

    fun write(target: File, writer: (OutputStream) -> Unit) = VaultOperationLock.withLock {
        val parent = requireNotNull(target.parentFile) { "文件缺少父目录" }
        check(parent.exists() || parent.mkdirs()) { "无法创建文件目录" }
        val temporary = File.createTempFile("${target.name}.tmp-", ".part", parent)
        try {
            FileOutputStream(temporary).use { output ->
                writer(output)
                output.fd.sync()
            }
            moveReplacing(temporary, target)
        } finally {
            temporary.delete()
        }
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
