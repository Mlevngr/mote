package com.mlevngr.inknote.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NoteLibraryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun library(): Pair<File, NoteLibrary> {
        val contextFiles = temporaryFolder.newFolder("context")
        val root = File(contextFiles, "notes")
        return root to NoteLibrary(root)
    }

    @Test fun createsFoldersAndNotesAndListsFoldersFirst() {
        val (root, library) = library()
        library.createNote("", "Ideas.md")
        val folder = library.createFolder("", "Work")
        val entries = library.list("")

        assertEquals(listOf("Work", "Ideas"), entries.map { it.name })
        assertEquals(NoteLibrary.EntryType.Folder, entries[0].type)
        assertEquals(NoteLibrary.EntryType.Note, entries[1].type)
        assertTrue(File(root, "Ideas/note.md").readText().startsWith("# Ideas"))
        assertEquals("", library.parentOf(folder.relativePath))
    }

    @Test fun supportsNestedFoldersAndUniqueNames() {
        val (_, library) = library()
        val folder = library.createFolder("", "Work")
        library.createNote(folder.relativePath, "Plan")
        library.createNote(folder.relativePath, "Plan")
        assertEquals(listOf("Plan", "Plan (2)"), library.list("Work").map { it.name })
        assertEquals("Work", library.parentOf("Work/Child"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTraversalNames() {
        val (_, library) = library()
        library.createFolder("", "../outside")
    }
}
