package com.mlevngr.inknote.library

import org.junit.Assert.assertEquals
import org.junit.Test

class TrashRetentionTest {
    @Test fun restoresStableIdsAndDefaultsToThirtyDays() {
        assertEquals(TrashRetention.SevenDays, TrashRetention.fromId("7_days"))
        assertEquals(TrashRetention.NinetyDays, TrashRetention.fromId("90_days"))
        assertEquals(TrashRetention.ThirtyDays, TrashRetention.fromId("future"))
        assertEquals(30L * 24L * 60L * 60L * 1_000L, TrashRetention.ThirtyDays.durationMillis)
    }
}
