package com.mlevngr.inknote.appearance

import com.mlevngr.inknote.library.FolderColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemePaletteTest {
    @Test fun everyThemeHasDistinctLightAndDarkPreviews() {
        AppTheme.entries.forEach { theme ->
            assertNotEquals(ThemePalette.preview(theme, false), ThemePalette.preview(theme, true))
        }
    }

    @Test fun folderColorsChangeWithTheSelectedTheme() {
        val colors = AppTheme.entries.map { theme ->
            ThemePalette.folderColor(theme, FolderColor.Purple, night = false)
        }

        assertEquals(AppTheme.entries.size, colors.distinct().size)
    }

    @Test fun everyThemeKeepsAllFolderColorChoicesDistinct() {
        AppTheme.entries.forEach { theme ->
            listOf(false, true).forEach { night ->
                val colors = FolderColor.entries.map { color ->
                    ThemePalette.folderColor(theme, color, night)
                }
                assertEquals(FolderColor.entries.size, colors.distinct().size)
            }
        }
    }

    @Test fun motePaletteRemainsBackwardCompatible() {
        FolderColor.entries.forEach { color ->
            assertEquals(color.light, ThemePalette.folderColor(AppTheme.InkNote, color, false))
            assertEquals(color.dark, ThemePalette.folderColor(AppTheme.InkNote, color, true))
        }
    }
}
