package com.mlevngr.inknote.library

enum class LibrarySort(val id: String) {
    NameAscending("name_ascending"),
    NameDescending("name_descending"),
    ModifiedAscending("modified_ascending"),
    ModifiedDescending("modified_descending");

    fun sort(entries: List<NoteLibrary.Entry>): List<NoteLibrary.Entry> {
        val byName = compareBy<NoteLibrary.Entry, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
            .thenBy { it.name }
            .thenBy { it.relativePath }
        val order = when (this) {
            NameAscending -> byName
            NameDescending -> byName.reversed()
            ModifiedAscending -> compareBy<NoteLibrary.Entry> { it.modifiedAt }.then(byName)
            ModifiedDescending -> compareByDescending<NoteLibrary.Entry> { it.modifiedAt }.then(byName)
        }
        return entries.sortedWith(
            compareBy<NoteLibrary.Entry> { it.type != NoteLibrary.EntryType.Folder }.then(order)
        )
    }

    companion object {
        fun fromId(id: String?): LibrarySort = entries.firstOrNull { it.id == id } ?: NameAscending
    }
}
