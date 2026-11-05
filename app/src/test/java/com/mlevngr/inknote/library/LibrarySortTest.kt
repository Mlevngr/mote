package com.mlevngr.inknote.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortTest {
    private val entries = listOf(
        entry("Zulu", NoteLibrary.EntryType.Note, 100),
        entry("beta", NoteLibrary.EntryType.Folder, 300),
        entry("Alpha", NoteLibrary.EntryType.Note, 200),
        entry("Alpha", NoteLibrary.EntryType.Folder, 100)
    )

    @Test fun sortsBothGroupsByNameInEitherDirection() {
        assertEquals(listOf("Alpha", "beta", "Alpha", "Zulu"), names(LibrarySort.NameAscending))
        assertEquals(listOf("beta", "Alpha", "Zulu", "Alpha"), names(LibrarySort.NameDescending))
    }

    @Test fun sortsBothGroupsByModificationTimeInEitherDirection() {
        assertEquals(listOf("Alpha", "beta", "Zulu", "Alpha"), names(LibrarySort.ModifiedAscending))
        assertEquals(listOf("beta", "Alpha", "Alpha", "Zulu"), names(LibrarySort.ModifiedDescending))
        LibrarySort.entries.forEach { sort ->
            assertEquals(
                listOf(NoteLibrary.EntryType.Folder, NoteLibrary.EntryType.Folder,
                    NoteLibrary.EntryType.Note, NoteLibrary.EntryType.Note),
                sort.sort(entries).map { it.type }
            )
        }
    }

    @Test fun equalTimestampsHaveStableNameOrder() {
        val tied = listOf(entry("z", NoteLibrary.EntryType.Note, 100), entry("A", NoteLibrary.EntryType.Note, 100))
        assertEquals(listOf("A", "z"), LibrarySort.ModifiedDescending.sort(tied).map { it.name })
        assertEquals(listOf("A", "z"), LibrarySort.ModifiedAscending.sort(tied.reversed()).map { it.name })
    }

    @Test fun restoresEverySettingAndDefaultsToExistingNameOrder() {
        LibrarySort.entries.forEach { assertEquals(it, LibrarySort.fromId(it.id)) }
        assertEquals(LibrarySort.NameAscending, LibrarySort.fromId(null))
        assertEquals(LibrarySort.NameAscending, LibrarySort.fromId("future_sort"))
        assertEquals(emptyList<NoteLibrary.Entry>(), LibrarySort.NameAscending.sort(emptyList()))
    }

    private fun names(sort: LibrarySort) = sort.sort(entries).map { it.name }

    private fun entry(name: String, type: NoteLibrary.EntryType, modified: Long) =
        NoteLibrary.Entry(name, "$name.${type.name}", type, modified)
}
