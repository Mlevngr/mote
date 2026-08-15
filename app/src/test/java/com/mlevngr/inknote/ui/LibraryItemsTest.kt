package com.mlevngr.inknote.ui

import com.mlevngr.inknote.appearance.LibraryLayoutMode
import com.mlevngr.inknote.library.NoteLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryItemsTest {
    private val folder = NoteLibrary.Entry("Work", "Work.folder", NoteLibrary.EntryType.Folder, 0)
    private val note = NoteLibrary.Entry("Plan", "Plan.note", NoteLibrary.EntryType.Note, 0)

    @Test fun samsungLayoutKeepsFoldersInTheirOwnTopSection() {
        val items = LibraryItems.build(listOf(folder, note), LibraryLayoutMode.Samsung, "Folders", "Notes")

        assertEquals("Folders", (items[0] as LibraryItem.Header).title)
        assertEquals(folder, (items[1] as LibraryItem.EntryItem).entry)
        assertEquals("Notes", (items[2] as LibraryItem.Header).title)
        assertEquals(note, (items[3] as LibraryItem.EntryItem).entry)
        assertEquals(1, LibraryItems.spanSize(items[1], LibraryLayoutMode.Samsung))
        assertEquals(2, LibraryItems.spanSize(items[3], LibraryLayoutMode.Samsung))
    }

    @Test fun gridUsesCardsAndListUsesFullWidthRows() {
        val grid = LibraryItems.build(listOf(folder, note), LibraryLayoutMode.Grid, "Folders", "Notes")
        val list = LibraryItems.build(listOf(folder, note), LibraryLayoutMode.List, "Folders", "Notes")

        assertTrue(grid.filterIsInstance<LibraryItem.EntryItem>().all { it.style != EntryStyle.List })
        assertTrue(list.all { it is LibraryItem.EntryItem && it.style == EntryStyle.List })
        assertTrue(list.all { LibraryItems.spanSize(it, LibraryLayoutMode.List) == 2 })
    }
}
