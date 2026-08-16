package com.mlevngr.inknote.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WebDavSyncStateTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun roundTripsUnicodePathsAndRejectsAnotherEndpointIdentity() {
        val state = WebDavSyncState(temporaryFolder.newFile("state.properties"))
        val records = mapOf("资料.folder/计划.note/note.md" to WebDavSyncRecord("hash", "etag"))

        state.save("server-a", records)

        assertEquals(records, state.load("server-a"))
        assertTrue(state.load("server-b").isEmpty())
    }
}
