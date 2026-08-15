package com.mlevngr.inknote.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val note = library.createNote("", "Ideas.md")
        val folder = library.createFolder("", "Work")
        val entries = library.list("")

        assertEquals("Ideas", note.name)
        assertEquals("Work", folder.name)
        assertEquals(listOf("Work", "Ideas"), entries.map { it.name })
        assertEquals(NoteLibrary.EntryType.Folder, entries[0].type)
        assertEquals(NoteLibrary.EntryType.Note, entries[1].type)
        assertTrue(File(root, "Ideas.note/note.md").readText().startsWith("# Ideas"))
        assertEquals("", library.parentOf(folder.relativePath))
    }

    @Test fun supportsNestedFoldersAndUniqueNames() {
        val (_, library) = library()
        val folder = library.createFolder("", "Work")
        library.createNote(folder.relativePath, "Plan")
        library.createNote(folder.relativePath, "Plan")
        assertEquals(listOf("Plan", "Plan (1)"), library.list(folder.relativePath).map { it.name })
        assertEquals(folder.relativePath, library.parentOf("${folder.relativePath}/Child.folder"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTraversalNames() {
        val (_, library) = library()
        library.createFolder("", "../outside")
    }

    @Test fun movesTheWholeNoteAndAssetsIntoAnotherFolder() {
        val (root, library) = library()
        val note = library.createNote("", "Travel")
        File(root, "Travel.note/assets/photo.jpg").writeText("image")
        val folder = library.createFolder("", "Archive")

        val moved = library.moveNote("", note.name, folder.relativePath)

        assertEquals("Archive.folder/Travel.note", moved.relativePath)
        assertTrue(File(root, "Archive.folder/Travel.note/note.md").isFile)
        assertTrue(File(root, "Archive.folder/Travel.note/assets/photo.jpg").isFile)
        assertTrue(!File(root, "Travel.note").exists())
    }

    @Test fun movingToFolderWithSameNameDoesNotOverwrite() {
        val (_, library) = library()
        val source = library.createNote("", "Plan")
        val archive = library.createFolder("", "Archive")
        library.createNote(archive.relativePath, "Plan")

        val moved = library.moveNote("", source.name, archive.relativePath)

        assertEquals("Archive.folder/Plan (1).note", moved.relativePath)
        assertEquals(listOf("Plan", "Plan (1)"), library.list(archive.relativePath).map { it.name })
    }

    @Test fun noteAndFolderMayUseTheSameDisplayName() {
        val (root, library) = library()
        val folder = library.createFolder("", "Project")
        val note = library.createNote("", "Project")

        assertEquals("Project.folder", folder.relativePath)
        assertEquals("Project.note", note.relativePath)
        assertTrue(File(root, "Project.folder").isDirectory)
        assertTrue(File(root, "Project.note/note.md").isFile)
        assertEquals(
            listOf(NoteLibrary.EntryType.Folder, NoteLibrary.EntryType.Note),
            library.list("").map { it.type }
        )
        assertEquals(listOf("Project", "Project"), library.list("").map { it.name })
    }

    @Test fun keepsLegacyNotesAndFoldersBrowsable() {
        val (root, library) = library()
        File(root, "Legacy Folder").mkdirs()
        File(root, "Legacy Note").mkdirs()
        File(root, "Legacy Note/note.md").writeText("legacy")

        assertEquals(
            listOf("Legacy Folder", "Legacy Note"),
            library.list("").map { it.name }
        )
        assertEquals("Legacy Folder", library.displayPath("Legacy Folder"))
        assertEquals("Legacy Note", library.findNote("", "Legacy Note").name)
        assertTrue(library.findNoteDirectory("", "Legacy Note").isDirectory)
    }

    @Test fun displaysNestedTypedFolderPathsWithoutStorageSuffixes() {
        val (_, library) = library()
        val parent = library.createFolder("", "Work")
        val child = library.createFolder(parent.relativePath, "Ideas")

        assertEquals("Work / Ideas", library.displayPath(child.relativePath))
    }

    @Test fun findsAndOpensChineseNamedFolderFromCurrentDirectory() {
        val (_, library) = library()
        val created = library.createFolder("", "资料")
        library.createNote(created.relativePath, "索引")

        val opened = library.findFolder("", "资料")

        assertEquals(created.relativePath, opened.relativePath)
        assertEquals(listOf("索引"), library.list(opened.relativePath).map { it.name })
    }

    @Test fun findsRootAndNestedNotesWithoutReparsingTheirRelativePaths() {
        val (_, library) = library()
        val rootNote = library.createNote("", "根笔记")
        val folder = library.createFolder("", "资料")
        val nestedNote = library.createNote(folder.relativePath, "子笔记")

        assertEquals(rootNote.name, library.findNote("", rootNote.name).name)
        assertEquals(
            nestedNote.relativePath,
            library.findNote(folder.relativePath, nestedNote.name).relativePath
        )
        assertTrue(library.findNoteDirectory(folder.relativePath, nestedNote.name).isDirectory)
    }

    @Test fun deletesTheWholeNoteIncludingAssets() {
        val (root, library) = library()
        val note = library.createNote("", "Disposable")
        File(root, "Disposable.note/assets/nested").mkdirs()
        File(root, "Disposable.note/assets/nested/photo.jpg").writeText("image")

        library.deleteNote("", note.name)

        assertTrue(!File(root, "Disposable.note").exists())
        assertTrue(library.list("").isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToDeleteFolderAsNote() {
        val (_, library) = library()
        val folder = library.createFolder("", "Keep")

        library.deleteNote("", folder.name)
    }

    @Test fun deletesLegacyWelcomeNoteByItsVisibleName() {
        val (root, library) = library()
        File(root, "welcome/assets").mkdirs()
        File(root, "welcome/note.md").writeText("legacy")

        library.deleteNote("", "welcome")

        assertTrue(!File(root, "welcome").exists())
    }

    @Test fun deletesChineseNamedNoteFromNestedFolder() {
        val (root, library) = library()
        val folder = library.createFolder("", "工作")
        val note = library.createNote(folder.relativePath, "会议记录")

        library.deleteNote(folder.relativePath, note.name)

        assertTrue(!File(root, "工作.folder/会议记录.note").exists())
        assertTrue(library.list(folder.relativePath).isEmpty())
    }

    @Test fun deletesNonEmptyFolderButKeepsSameNamedNote() {
        val (root, library) = library()
        val folder = library.createFolder("", "Project")
        val rootNote = library.createNote("", "Project")
        val nestedNote = library.createNote(folder.relativePath, "Nested")
        File(root, "${nestedNote.relativePath}/assets/photo.jpg").writeText("image")

        library.deleteFolder("", folder.name)

        assertTrue(!File(root, folder.relativePath).exists())
        assertTrue(File(root, "${rootNote.relativePath}/note.md").isFile)
        assertEquals(listOf(NoteLibrary.EntryType.Note), library.list("").map { it.type })
    }

    @Test fun pathPolicyAcceptsChildrenAndRejectsTraversalAndLookalikes() {
        val (root, _) = library()
        File(root, "Child.folder").mkdirs()
        val lookalike = File(root.parentFile, "${root.name}-other").also(File::mkdirs)

        assertEquals(
            File(root, "Child.folder").canonicalFile,
            NotePathPolicy.resolve(root, "Child.folder")
        )
        assertNull(NotePathPolicy.resolve(root, "../${lookalike.name}"))
        assertNull(NotePathPolicy.resolve(root, lookalike.absolutePath))
    }

    @Test fun renamesNoteAndKeepsMarkdownAndAssets() {
        val (root, library) = library()
        val note = library.createNote("", "Draft")
        File(root, "${note.relativePath}/note.md").writeText("custom markdown")
        File(root, "${note.relativePath}/assets/photo.jpg").writeText("image")

        val renamed = library.renameNote("", note.name, "Final.md")

        assertEquals("Final", renamed.name)
        assertEquals("Final.note", renamed.relativePath)
        assertEquals("custom markdown", File(root, "Final.note/note.md").readText())
        assertEquals("image", File(root, "Final.note/assets/photo.jpg").readText())
        assertTrue(!File(root, note.relativePath).exists())
    }

    @Test fun renamesFolderAndKeepsNestedNotes() {
        val (root, library) = library()
        val folder = library.createFolder("", "Old")
        library.createNote(folder.relativePath, "Nested")

        val renamed = library.renameFolder("", folder.name, "New")

        assertEquals("New", renamed.name)
        assertTrue(File(root, "New.folder/Nested.note/note.md").isFile)
        assertEquals(listOf("Nested"), library.list(renamed.relativePath).map { it.name })
    }

    @Test fun renameAllowsNoteAndFolderToShareDisplayName() {
        val (_, library) = library()
        val folder = library.createFolder("", "Shared")
        val note = library.createNote("", "Draft")

        val renamed = library.renameNote("", note.name, folder.name)

        assertEquals("Shared", renamed.name)
        assertEquals(
            listOf(NoteLibrary.EntryType.Folder, NoteLibrary.EntryType.Note),
            library.list("").map { it.type }
        )
    }

    @Test fun renameRefusesSameTypeCollisionWithoutOverwriting() {
        val (root, library) = library()
        val first = library.createNote("", "First")
        val second = library.createNote("", "Second")
        File(root, "${first.relativePath}/note.md").writeText("first")
        File(root, "${second.relativePath}/note.md").writeText("second")

        val failure = runCatching { library.renameNote("", second.name, first.name) }.exceptionOrNull()

        assertEquals("同名笔记已存在", failure?.message)
        assertEquals("first", File(root, "${first.relativePath}/note.md").readText())
        assertEquals("second", File(root, "${second.relativePath}/note.md").readText())
    }
}
