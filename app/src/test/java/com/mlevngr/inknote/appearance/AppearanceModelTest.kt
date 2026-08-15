package com.mlevngr.inknote.appearance

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceModelTest {
    @Test fun restoresStableThemeIdsAndFallsBackSafely() {
        assertEquals(AppTheme.Catppuccin, AppTheme.fromId("catppuccin"))
        assertEquals(AppTheme.TokyoNight, AppTheme.fromId("tokyo_night"))
        assertEquals(AppTheme.InkNote, AppTheme.fromId("future_theme"))
    }

    @Test fun restoresLayoutAndPreviewIdsAndFallsBackToVisualDefaults() {
        assertEquals(LibraryLayoutMode.Grid, LibraryLayoutMode.fromId("grid"))
        assertEquals(LibraryLayoutMode.Samsung, LibraryLayoutMode.fromId(null))
        assertEquals(NotePreviewMode.TitleOnly, NotePreviewMode.fromId("title_only"))
        assertEquals(NotePreviewMode.Thumbnail, NotePreviewMode.fromId("unknown"))
    }
}
