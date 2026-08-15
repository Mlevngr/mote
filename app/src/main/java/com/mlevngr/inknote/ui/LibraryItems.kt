package com.mlevngr.inknote.ui

import com.mlevngr.inknote.appearance.LibraryLayoutMode
import com.mlevngr.inknote.library.NoteLibrary

sealed interface LibraryItem {
    data class Header(val title: String) : LibraryItem
    data class EntryItem(val entry: NoteLibrary.Entry, val style: EntryStyle) : LibraryItem
}

enum class EntryStyle { List, FolderCard, NoteCard }

object LibraryItems {
    fun build(
        entries: List<NoteLibrary.Entry>,
        mode: LibraryLayoutMode,
        foldersLabel: String,
        notesLabel: String
    ): List<LibraryItem> {
        val folders = entries.filter { it.type == NoteLibrary.EntryType.Folder }
        val notes = entries.filter { it.type == NoteLibrary.EntryType.Note }
        return when (mode) {
            LibraryLayoutMode.List -> entries.map { LibraryItem.EntryItem(it, EntryStyle.List) }
            LibraryLayoutMode.Samsung -> buildList {
                if (folders.isNotEmpty()) {
                    add(LibraryItem.Header(foldersLabel))
                    addAll(folders.map { LibraryItem.EntryItem(it, EntryStyle.FolderCard) })
                }
                if (notes.isNotEmpty()) {
                    add(LibraryItem.Header(notesLabel))
                    addAll(notes.map { LibraryItem.EntryItem(it, EntryStyle.List) })
                }
            }
            LibraryLayoutMode.Grid -> buildList {
                if (folders.isNotEmpty()) {
                    add(LibraryItem.Header(foldersLabel))
                    addAll(folders.map { LibraryItem.EntryItem(it, EntryStyle.FolderCard) })
                }
                if (notes.isNotEmpty()) {
                    add(LibraryItem.Header(notesLabel))
                    addAll(notes.map { LibraryItem.EntryItem(it, EntryStyle.NoteCard) })
                }
            }
        }
    }

    fun spanSize(item: LibraryItem, mode: LibraryLayoutMode): Int = when {
        item is LibraryItem.Header -> 2
        mode == LibraryLayoutMode.List -> 2
        mode == LibraryLayoutMode.Samsung &&
            (item as LibraryItem.EntryItem).style == EntryStyle.List -> 2
        else -> 1
    }
}
