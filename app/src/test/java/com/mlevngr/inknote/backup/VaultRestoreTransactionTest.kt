package com.mlevngr.inknote.backup

import com.mlevngr.inknote.storage.VaultRestoreTransaction

import com.mlevngr.inknote.library.NoteLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class VaultRestoreTransactionTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun recoversOriginalVaultWhenProcessStopsAfterMovingItAside() {
        val vault = temporaryFolder.newFolder("notes")
        File(vault, "original.md").writeText("original")
        val transaction = VaultRestoreTransaction.start(vault)
        File(transaction.stagingDirectory, "restored.md").writeText("restored")
        transaction.markPrepared()

        Files.move(vault.toPath(), transaction.rollbackDirectory.toPath())

        NoteLibrary(vault)

        assertEquals("original", File(vault, "original.md").readText())
        assertFalse(transaction.stagingDirectory.exists())
        assertFalse(transaction.rollbackDirectory.exists())
        assertFalse(transaction.journalFile.exists())
    }

    @Test fun finishesInstalledVaultWhenProcessStopsBeforeRecordingNewState() {
        val vault = temporaryFolder.newFolder("notes-installed")
        File(vault, "original.md").writeText("original")
        val transaction = VaultRestoreTransaction.start(vault)
        File(transaction.stagingDirectory, "restored.md").writeText("restored")
        transaction.markPrepared()
        Files.move(vault.toPath(), transaction.rollbackDirectory.toPath())
        transaction.markOldMoved()
        Files.move(transaction.stagingDirectory.toPath(), vault.toPath())

        VaultRestoreTransaction.recover(vault)

        assertEquals("restored", File(vault, "restored.md").readText())
        assertFalse(File(vault, "original.md").exists())
        assertFalse(transaction.rollbackDirectory.exists())
        assertFalse(transaction.journalFile.exists())
    }

    @Test fun finishesFirstRestoreWhenThereWasNoPreviousVault() {
        val vault = File(temporaryFolder.root, "new-notes")
        val transaction = VaultRestoreTransaction.start(vault)
        File(transaction.stagingDirectory, "restored.md").writeText("restored")
        transaction.markPrepared()
        transaction.markOldMoved()

        VaultRestoreTransaction.recover(vault)

        assertTrue(vault.isDirectory)
        assertEquals("restored", File(vault, "restored.md").readText())
        assertFalse(transaction.journalFile.exists())
    }

    @Test fun startupRemovesOnlyAnUnjournaledStagingTreeForThisVault() {
        val vault = File(temporaryFolder.root, "orphan-notes")
        val orphan = File(
            temporaryFolder.root,
            ".mote-restore-stage-${vault.name}-interrupted"
        ).also(File::mkdirs)
        File(orphan, "partial.md").writeText("partial")
        val unrelated = File(
            temporaryFolder.root,
            ".mote-restore-stage-other-vault-keep"
        ).also(File::mkdirs)

        NoteLibrary(vault)

        assertFalse(orphan.exists())
        assertTrue(unrelated.exists())
        assertTrue(vault.isDirectory)
    }
}
