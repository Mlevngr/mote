package com.mlevngr.inknote.appearance

import android.app.Activity
import android.content.Context
import androidx.annotation.StyleRes
import androidx.core.content.edit
import com.mlevngr.inknote.R
import com.mlevngr.inknote.library.LibrarySort

enum class AppTheme(val id: String, @param:StyleRes val styleRes: Int) {
    InkNote("inknote", R.style.Theme_InkNote),
    Catppuccin("catppuccin", R.style.Theme_InkNote_Catppuccin),
    TokyoNight("tokyo_night", R.style.Theme_InkNote_TokyoNight),
    Minimal("minimal", R.style.Theme_InkNote_Minimal);

    companion object {
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: InkNote
    }
}

enum class LibraryLayoutMode(val id: String) {
    Samsung("samsung"),
    List("list"),
    Grid("grid");

    companion object {
        fun fromId(id: String?): LibraryLayoutMode = entries.firstOrNull { it.id == id } ?: Samsung
    }
}

enum class NotePreviewMode(val id: String) {
    RenderedPage("rendered_page"),
    Summary("summary"),
    TitleOnly("title_only");

    companion object {
        fun fromId(id: String?): NotePreviewMode = when (id) {
            "thumbnail" -> RenderedPage
            else -> entries.firstOrNull { it.id == id } ?: RenderedPage
        }
    }
}

class AppearancePreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val skinPreferences = SkinPreferences(context)

    var theme: AppTheme
        get() = AppTheme.fromId(preferences.getString(KEY_THEME, null))
        set(value) {
            preferences.edit { putString(KEY_THEME, value.id) }
            skinPreferences.disable()
        }

    var libraryLayout: LibraryLayoutMode
        get() = LibraryLayoutMode.fromId(preferences.getString(KEY_LIBRARY_LAYOUT, null))
        set(value) { preferences.edit { putString(KEY_LIBRARY_LAYOUT, value.id) } }

    var notePreview: NotePreviewMode
        get() = NotePreviewMode.fromId(preferences.getString(KEY_NOTE_PREVIEW, null))
        set(value) { preferences.edit { putString(KEY_NOTE_PREVIEW, value.id) } }

    var librarySort: LibrarySort
        get() = LibrarySort.fromId(preferences.getString(KEY_LIBRARY_SORT, null))
        set(value) { preferences.edit { putString(KEY_LIBRARY_SORT, value.id) } }

    fun applyTheme(activity: Activity) {
        activity.setTheme(if (SkinTheme.isApplied(activity)) R.style.Theme_InkNote_Skin else theme.styleRes)
    }

    private companion object {
        const val PREFERENCES = "appearance"
        const val KEY_THEME = "theme"
        const val KEY_LIBRARY_LAYOUT = "library_layout"
        const val KEY_NOTE_PREVIEW = "note_preview"
        const val KEY_LIBRARY_SORT = "library_sort"
    }
}
