package com.mlevngr.inknote.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NoteLibraryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val rootLocation = NoteLibrary.FolderLocation.Root

    private fun library(): Pair<File, NoteLibrary> {
        val contextFiles = temporaryFolder.newFolder("context")
        val root = File(contextFiles, "notes")
        return root to NoteLibrary(root)
    }

    @Test fun createsFoldersAndNotesAndListsFoldersFirst() {
        val (root, library) = library()
        val note = library.createNote(rootLocation, "Ideas.md")
        val folder = library.createFolder(rootLocation, "Work")
        val entries = library.list(rootLocation)

        assertEquals("Ideas", note.name)
        assertEquals("Work", folder.name)
        assertEquals(listOf("Work", "Ideas"), entries.map { it.name })
        assertEquals(NoteLibrary.EntryType.Folder, entries[0].type)
        assertEquals(NoteLibrary.EntryType.Note, entries[1].type)
        assertEquals("", File(root, "Ideas.note/note.md").readText())
        assertTrue(File(root, "Ideas.note/${NoteLibrary.BODY_SEPARATION_MARKER}").isFile)
        assertEquals(rootLocation, rootLocation.child(folder.name).parent())
    }

    @Test fun supportsNestedFoldersAndUniqueNames() {
        val (_, library) = library()
        val folder = library.createFolder(rootLocation, "Work")
        val work = rootLocation.child(folder.name)
        library.createNote(work, "Plan")
        library.createNote(work, "Plan")
        assertEquals(listOf("Plan", "Plan (1)"), library.list(work).map { it.name })
        assertEquals(work, work.child("Child").parent())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTraversalNames() {
        val (_, library) = library()
        library.createFolder(rootLocation, "../outside")
    }

    @Test fun movesTheWholeNoteAndAssetsIntoAnotherFolder() {
        val (root, library) = library()
        val note = library.createNote(rootLocation, "Travel")
        File(root, "Travel.note/assets/photo.jpg").writeText("image")
        val folder = library.createFolder(rootLocation, "Archive")

        val moved = library.moveNote(rootLocation, note.name, rootLocation.child(folder.name))

        assertEquals("Archive.folder/Travel.note", moved.relativePath)
        assertTrue(File(root, "Archive.folder/Travel.note/note.md").isFile)
        assertTrue(File(root, "Archive.folder/Travel.note/assets/photo.jpg").isFile)
        assertTrue(!File(root, "Travel.note").exists())
    }

    @Test fun movingToFolderWithSameNameDoesNotOverwrite() {
        val (_, library) = library()
        val source = library.createNote(rootLocation, "Plan")
        val archive = library.createFolder(rootLocation, "Archive")
        val archiveLocation = rootLocation.child(archive.name)
        library.createNote(archiveLocation, "Plan")

        val moved = library.moveNote(rootLocation, source.name, archiveLocation)

        assertEquals("Archive.folder/Plan (1).note", moved.relativePath)
        assertEquals(listOf("Plan", "Plan (1)"), library.list(archiveLocation).map { it.name })
    }

    @Test fun noteAndFolderMayUseTheSameDisplayName() {
        val (root, library) = library()
        val folder = library.createFolder(rootLocation, "Project")
        val note = library.createNote(rootLocation, "Project")

        assertEquals("Project.folder", folder.relativePath)
        assertEquals("Project.note", note.relativePath)
        assertTrue(File(root, "Project.folder").isDirectory)
        assertTrue(File(root, "Project.note/note.md").isFile)
        assertEquals(
            listOf(NoteLibrary.EntryType.Folder, NoteLibrary.EntryType.Note),
            library.list(rootLocation).map { it.type }
        )
        assertEquals(listOf("Project", "Project"), library.list(rootLocation).map { it.name })
    }

    @Test fun keepsLegacyNotesAndFoldersBrowsable() {
        val (root, library) = library()
        File(root, "Legacy Folder").mkdirs()
        File(root, "Legacy Note").mkdirs()
        File(root, "Legacy Note/note.md").writeText("legacy")

        assertEquals(
            listOf("Legacy Folder", "Legacy Note"),
            library.list(rootLocation).map { it.name }
        )
        assertEquals("Legacy Note", library.findNote(rootLocation, "Legacy Note").name)
        assertTrue(library.findNoteDirectory(rootLocation, "Legacy Note").isDirectory)
    }

    @Test fun displaysNestedTypedFolderPathsWithoutStorageSuffixes() {
        val (_, library) = library()
        val parent = library.createFolder(rootLocation, "Work")
        val parentLocation = rootLocation.child(parent.name)
        val child = library.createFolder(parentLocation, "Ideas")

        assertEquals("Work / Ideas", parentLocation.child(child.name).displayPath)
    }

    @Test fun findsAndOpensChineseNamedFolderFromCurrentDirectory() {
        val (_, library) = library()
        val created = library.createFolder(rootLocation, "资料")
        val location = rootLocation.child(created.name)
        library.createNote(location, "索引")

        val opened = library.findFolder(rootLocation, "资料")

        assertEquals(created.relativePath, opened.relativePath)
        assertEquals(listOf("索引"), library.list(location).map { it.name })
    }

    @Test fun findsRootAndNestedNotesWithoutReparsingTheirRelativePaths() {
        val (_, library) = library()
        val rootNote = library.createNote(rootLocation, "根笔记")
        val folder = library.createFolder(rootLocation, "资料")
        val location = rootLocation.child(folder.name)
        val nestedNote = library.createNote(location, "子笔记")

        assertEquals(rootNote.name, library.findNote(rootLocation, rootNote.name).name)
        assertEquals(
            nestedNote.relativePath,
            library.findNote(location, nestedNote.name).relativePath
        )
        assertTrue(library.findNoteDirectory(location, nestedNote.name).isDirectory)
    }

    @Test fun deletesTheWholeNoteIncludingAssets() {
        val (root, library) = library()
        val note = library.createNote(rootLocation, "Disposable")
        File(root, "Disposable.note/assets/nested").mkdirs()
        File(root, "Disposable.note/assets/nested/photo.jpg").writeText("image")

        library.deleteNote(rootLocation, note.name)

        assertTrue(!File(root, "Disposable.note").exists())
        assertTrue(library.list(rootLocation).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToDeleteFolderAsNote() {
        val (_, library) = library()
        val folder = library.createFolder(rootLocation, "Keep")

        library.deleteNote(rootLocation, folder.name)
    }

    @Test fun deletesLegacyWelcomeNoteByItsVisibleName() {
        val (root, library) = library()
        File(root, "welcome/assets").mkdirs()
        File(root, "welcome/note.md").writeText("legacy")

        library.deleteNote(rootLocation, "welcome")

        assertTrue(!File(root, "welcome").exists())
    }

    @Test fun deletesChineseNamedNoteFromNestedFolder() {
        val (root, library) = library()
        val folder = library.createFolder(rootLocation, "工作")
        val location = rootLocation.child(folder.name)
        val note = library.createNote(location, "会议记录")

        library.deleteNote(location, note.name)

        assertTrue(!File(root, "工作.folder/会议记录.note").exists())
        assertTrue(library.list(location).isEmpty())
    }

    @Test fun deletesNonEmptyFolderButKeepsSameNamedNote() {
        val (root, library) = library()
        val folder = library.createFolder(rootLocation, "Project")
        val rootNote = library.createNote(rootLocation, "Project")
        val nestedNote = library.createNote(rootLocation.child(folder.name), "Nested")
        File(root, "${nestedNote.relativePath}/assets/photo.jpg").writeText("image")

        library.deleteFolder(rootLocation, folder.name)

        assertTrue(!File(root, folder.relativePath).exists())
        assertTrue(File(root, "${rootNote.relativePath}/note.md").isFile)
        assertEquals(listOf(NoteLibrary.EntryType.Note), library.list(rootLocation).map { it.type })
    }

    @Test fun renamesNoteAndKeepsMarkdownAndAssets() {
        val (root, library) = library()
        val note = library.createNote(rootLocation, "Draft")
        File(root, "${note.relativePath}/note.md").writeText("custom markdown")
        File(root, "${note.relativePath}/assets/photo.jpg").writeText("image")

        val renamed = library.renameNote(rootLocation, note.name, "Final.md")

        assertEquals("Final", renamed.name)
        assertEquals("Final.note", renamed.relativePath)
        assertEquals("custom markdown", File(root, "Final.note/note.md").readText())
        assertEquals("image", File(root, "Final.note/assets/photo.jpg").readText())
        assertTrue(!File(root, note.relativePath).exists())
    }

    @Test fun renamesFolderAndKeepsNestedNotes() {
        val (root, library) = library()
        val folder = library.createFolder(rootLocation, "Old")
        val oldLocation = rootLocation.child(folder.name)
        library.createNote(oldLocation, "Nested")

        val renamed = library.renameFolder(rootLocation, folder.name, "New")

        assertEquals("New", renamed.name)
        assertTrue(File(root, "New.folder/Nested.note/note.md").isFile)
        assertEquals(listOf("Nested"), library.list(rootLocation.child(renamed.name)).map { it.name })
    }

    @Test fun renameAllowsNoteAndFolderToShareDisplayName() {
        val (_, library) = library()
        val folder = library.createFolder(rootLocation, "Shared")
        val note = library.createNote(rootLocation, "Draft")

        val renamed = library.renameNote(rootLocation, note.name, folder.name)

        assertEquals("Shared", renamed.name)
        assertEquals(
            listOf(NoteLibrary.EntryType.Folder, NoteLibrary.EntryType.Note),
            library.list(rootLocation).map { it.type }
        )
    }

    @Test fun renameRefusesSameTypeCollisionWithoutOverwriting() {
        val (root, library) = library()
        val first = library.createNote(rootLocation, "First")
        val second = library.createNote(rootLocation, "Second")
        File(root, "${first.relativePath}/note.md").writeText("first")
        File(root, "${second.relativePath}/note.md").writeText("second")

        val failure = runCatching { library.renameNote(rootLocation, second.name, first.name) }.exceptionOrNull()

        assertEquals("同名笔记已存在", failure?.message)
        assertEquals("first", File(root, "${first.relativePath}/note.md").readText())
        assertEquals("second", File(root, "${second.relativePath}/note.md").readText())
    }

    @Test fun movesFolderWithNestedNotesAndAssets() {
        val (root, library) = library()
        val source = library.createFolder(rootLocation, "Source")
        val sourceLocation = rootLocation.child(source.name)
        val nested = library.createFolder(sourceLocation, "Nested")
        val nestedLocation = sourceLocation.child(nested.name)
        val note = library.createNote(nestedLocation, "Note")
        File(root, "${note.relativePath}/assets/photo.jpg").writeText("image")
        val target = library.createFolder(rootLocation, "Target")
        val targetLocation = rootLocation.child(target.name)

        val moved = library.moveFolder(rootLocation, source.name, targetLocation)

        assertEquals("Target.folder/Source.folder", moved.relativePath)
        assertTrue(
            File(root, "Target.folder/Source.folder/Nested.folder/Note.note/assets/photo.jpg").isFile
        )
        assertTrue(!File(root, "Source.folder").exists())
        assertEquals(
            listOf("Note"),
            library.list(targetLocation.child("Source").child("Nested")).map { it.name }
        )
    }

    @Test fun refusesToMoveFolderIntoItsDescendant() {
        val (_, library) = library()
        val parent = library.createFolder(rootLocation, "Parent")
        val parentLocation = rootLocation.child(parent.name)
        val child = library.createFolder(parentLocation, "Child")
        val childLocation = parentLocation.child(child.name)

        val failure = runCatching {
            library.moveFolder(rootLocation, parent.name, childLocation)
        }.exceptionOrNull()

        assertEquals("不能把文件夹移动到自身或其子文件夹中", failure?.message)
        assertEquals(listOf("Child"), library.list(parentLocation).map { it.name })
    }

    @Test fun listsNestedFolderLocationsUsingOnlyVisibleNames() {
        val (_, library) = library()
        val parent = library.createFolder(rootLocation, "资料")
        library.createFolder(rootLocation.child(parent.name), "图片")

        assertEquals(
            listOf("资料", "资料 / 图片"),
            library.listFolderLocations().map { it.displayPath }
        )
    }
}
