package com.mlevngr.inknote.storage

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID

/**
 * A durable two-directory restore transaction. The journal lives beside the vault so startup can
 * either finish installing a validated staging tree or put the previous tree back after process
 * death. Journal paths are stored as constrained child names, never as arbitrary absolute paths.
 */
internal class VaultRestoreTransaction private constructor(
    private val vaultRoot: File,
    internal val stagingDirectory: File,
    internal val rollbackDirectory: File,
    private val hadExistingVault: Boolean
) {
    internal val journalFile: File get() = journalFor(vaultRoot)

    fun commit() {
        markPrepared()
        if (hadExistingVault) move(vaultRoot, rollbackDirectory)
        markOldMoved()
        try {
            move(stagingDirectory, vaultRoot)
        } catch (error: Exception) {
            if (hadExistingVault && rollbackDirectory.exists() && !vaultRoot.exists()) {
                runCatching { move(rollbackDirectory, vaultRoot) }
            }
            throw error
        }
        writeState(State.NEW_INSTALLED)
        deleteTree(rollbackDirectory)
        writeState(State.COMPLETED)
        check(journalFile.delete() || !journalFile.exists()) { "无法完成恢复事务" }
    }

    internal fun markPrepared() = writeState(State.PREPARED)

    internal fun markOldMoved() = writeState(State.OLD_MOVED)

    fun discardUnprepared() {
        if (!journalFile.exists()) deleteTree(stagingDirectory)
    }

    private fun writeState(state: State) {
        AtomicFileWriter.write(journalFile) { output ->
            Properties().apply {
                setProperty(KEY_FORMAT_VERSION, JOURNAL_FORMAT_VERSION)
                setProperty(KEY_VAULT_NAME, vaultRoot.name)
                setProperty(KEY_STAGING_NAME, stagingDirectory.name)
                setProperty(KEY_ROLLBACK_NAME, rollbackDirectory.name)
                setProperty(KEY_HAD_EXISTING, hadExistingVault.toString())
                setProperty(KEY_STATE, state.name)
            }.store(output, "Mote vault restore transaction")
        }
    }

    internal enum class State { PREPARED, OLD_MOVED, NEW_INSTALLED, COMPLETED }

    companion object {
        private const val JOURNAL_FORMAT_VERSION = "1"
        private const val KEY_FORMAT_VERSION = "formatVersion"
        private const val KEY_VAULT_NAME = "vaultName"
        private const val KEY_STAGING_NAME = "stagingName"
        private const val KEY_ROLLBACK_NAME = "rollbackName"
        private const val KEY_HAD_EXISTING = "hadExistingVault"
        private const val KEY_STATE = "state"
        private const val STAGING_PREFIX = ".mote-restore-stage-"
        private const val ROLLBACK_PREFIX = ".mote-restore-old-"

        fun start(vaultRoot: File): VaultRestoreTransaction {
            recover(vaultRoot)
            val parent = requireNotNull(vaultRoot.parentFile) { "笔记库缺少父目录" }
            check(parent.exists() || parent.mkdirs()) { "无法创建笔记库目录" }
            val staging = File(parent, "${stagingPrefix(vaultRoot)}${UUID.randomUUID()}")
            val rollback = File(parent, "${rollbackPrefix(vaultRoot)}${UUID.randomUUID()}")
            check(staging.mkdir()) { "无法创建恢复暂存目录" }
            return VaultRestoreTransaction(vaultRoot, staging, rollback, vaultRoot.exists())
        }

        fun recover(vaultRoot: File) {
            val journal = journalFor(vaultRoot)
            if (!journal.isFile) {
                cleanupOrphanStaging(vaultRoot)
                cleanupJournalTemporaryFiles(journal)
                return
            }
            val record = readRecord(vaultRoot, journal)
            when (record.state) {
                State.PREPARED -> recoverPrepared(vaultRoot, record)
                State.OLD_MOVED -> recoverOldMoved(vaultRoot, record)
                State.NEW_INSTALLED,
                State.COMPLETED -> recoverInstalled(vaultRoot, record)
            }
            deleteTree(record.staging)
            deleteTree(record.rollback)
            check(journal.delete() || !journal.exists()) { "无法清理恢复事务日志" }
            cleanupJournalTemporaryFiles(journal)
        }

        private fun recoverPrepared(vaultRoot: File, record: Record) {
            when {
                vaultRoot.exists() -> Unit
                record.rollback.exists() -> move(record.rollback, vaultRoot)
                !record.hadExistingVault -> Unit
                else -> error("恢复事务缺少原笔记库，已停止以避免数据丢失")
            }
        }

        private fun recoverOldMoved(vaultRoot: File, record: Record) {
            if (vaultRoot.exists()) return
            if (record.staging.exists()) {
                runCatching { move(record.staging, vaultRoot) }.getOrElse { installError ->
                    if (record.rollback.exists() && !vaultRoot.exists()) {
                        runCatching { move(record.rollback, vaultRoot) }
                    }
                    throw installError
                }
                return
            }
            if (record.rollback.exists()) {
                move(record.rollback, vaultRoot)
                return
            }
            error("恢复事务缺少暂存笔记库，已停止以避免数据丢失")
        }

        private fun recoverInstalled(vaultRoot: File, record: Record) {
            if (vaultRoot.exists()) return
            when {
                record.staging.exists() -> move(record.staging, vaultRoot)
                record.rollback.exists() -> move(record.rollback, vaultRoot)
                else -> error("恢复事务没有可用的笔记库，已停止以避免数据丢失")
            }
        }

        private fun readRecord(vaultRoot: File, journal: File): Record {
            val properties = Properties().apply { journal.inputStream().use(::load) }
            require(properties.getProperty(KEY_FORMAT_VERSION) == JOURNAL_FORMAT_VERSION) {
                "不支持此恢复事务版本"
            }
            require(properties.getProperty(KEY_VAULT_NAME) == vaultRoot.name) {
                "恢复事务与当前笔记库不匹配"
            }
            val parent = requireNotNull(vaultRoot.parentFile).canonicalFile
            val staging = safeChild(
                parent,
                properties.getProperty(KEY_STAGING_NAME),
                stagingPrefix(vaultRoot)
            )
            val rollback = safeChild(
                parent,
                properties.getProperty(KEY_ROLLBACK_NAME),
                rollbackPrefix(vaultRoot)
            )
            return Record(
                state = State.valueOf(requireNotNull(properties.getProperty(KEY_STATE))),
                staging = staging,
                rollback = rollback,
                hadExistingVault = properties.getProperty(KEY_HAD_EXISTING).toBooleanStrict()
            )
        }

        private fun safeChild(parent: File, name: String?, prefix: String): File {
            require(!name.isNullOrBlank() && name.startsWith(prefix)) { "恢复事务包含无效路径" }
            require(name == File(name).name) { "恢复事务包含无效路径" }
            val child = File(parent, name).canonicalFile
            require(child.parentFile == parent) { "恢复事务路径越过笔记库目录" }
            return child
        }

        private fun journalFor(vaultRoot: File): File = File(
            requireNotNull(vaultRoot.parentFile) { "笔记库缺少父目录" },
            ".mote-restore-${vaultRoot.name}.properties"
        )

        private fun stagingPrefix(vaultRoot: File): String = "$STAGING_PREFIX${vaultRoot.name}-"

        private fun rollbackPrefix(vaultRoot: File): String = "$ROLLBACK_PREFIX${vaultRoot.name}-"

        private fun cleanupOrphanStaging(vaultRoot: File) {
            val parent = vaultRoot.parentFile ?: return
            parent.listFiles().orEmpty()
                .filter { it.name.startsWith(stagingPrefix(vaultRoot)) }
                .forEach(::deleteTree)
        }

        private fun cleanupJournalTemporaryFiles(journal: File) {
            journal.parentFile?.listFiles().orEmpty()
                .filter { it.name.startsWith("${journal.name}.tmp-") && it.name.endsWith(".part") }
                .forEach(::deleteTree)
        }

        private fun move(source: File, target: File) {
            try {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(source.toPath(), target.toPath())
            }
        }

        private fun deleteTree(file: File) {
            if (file.exists()) check(file.deleteRecursively() && !file.exists()) { "无法清理恢复临时文件" }
        }

        private data class Record(
            val state: State,
            val staging: File,
            val rollback: File,
            val hadExistingVault: Boolean
        )
    }
}
