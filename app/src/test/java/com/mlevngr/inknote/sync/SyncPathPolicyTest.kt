package com.mlevngr.inknote.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncPathPolicyTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun resolvesSafeNestedPathsInsideTheLibrary() {
        val root = temporaryFolder.newFolder("notes")
        val target = SyncPathPolicy.resolve(root, "Folder.folder/Note.note/note.md")
        assertEquals(root.resolve("Folder.folder/Note.note/note.md").canonicalFile, target)
    }

    @Test fun rejectsParentTraversalAndBlankSegments() {
        val root = temporaryFolder.newFolder("notes")
        assertThrows(IllegalArgumentException::class.java) { SyncPathPolicy.resolve(root, "../secret") }
        assertThrows(IllegalArgumentException::class.java) { SyncPathPolicy.resolve(root, "folder//note.md") }
    }
}
