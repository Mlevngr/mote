package com.mlevngr.inknote.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupScheduleTest {
    @Test fun firstBackupIsAlwaysDue() {
        assertTrue(BackupSchedule.isDue(0L, 100L, 24L))
    }

    @Test fun waitsForFullIntervalAfterSuccessfulBackup() {
        assertFalse(BackupSchedule.isDue(100L, 123L, 24L))
        assertTrue(BackupSchedule.isDue(100L, 124L, 24L))
    }

    @Test fun clockMovingBackDoesNotCreateBackupLoop() {
        assertFalse(BackupSchedule.isDue(200L, 100L, 24L))
    }
}
