package com.mlevngr.inknote.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupRetentionPolicyTest {
    @Test fun keepsNewestBackupsAndDeletesOldest() {
        val backups = (1L..5L).map { BackupRecord("backup-$it.zip", it) }

        val expired = BackupRetentionPolicy(keepCount = 3).expired(backups)

        assertEquals(listOf("backup-1.zip", "backup-2.zip"), expired.map { it.name })
    }

    @Test fun deterministicNameBreaksTimestampTies() {
        val backups = listOf(
            BackupRecord("Mote-backup-b.zip", 10L),
            BackupRecord("Mote-backup-a.zip", 10L)
        )

        val expired = BackupRetentionPolicy(keepCount = 1).expired(backups)

        assertEquals(listOf("Mote-backup-a.zip"), expired.map { it.name })
    }
}
