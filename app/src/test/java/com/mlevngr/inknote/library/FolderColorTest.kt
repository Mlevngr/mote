package com.mlevngr.inknote.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderColorTest {
    @Test fun keepsExistingColorIdsAndLoadsNewPaletteColors() {
        assertEquals(FolderColor.Blue, FolderColor.fromId("blue"))
        assertEquals(FolderColor.Purple, FolderColor.fromId("purple"))
        assertEquals(FolderColor.Green, FolderColor.fromId("green"))
        assertEquals(FolderColor.Indigo, FolderColor.fromId("indigo"))
        assertEquals(FolderColor.Teal, FolderColor.fromId("teal"))
        assertEquals(FolderColor.Brown, FolderColor.fromId("brown"))
    }

    @Test fun exposesTwelveDistinctMaterialPaletteOptions() {
        assertEquals(12, FolderColor.entries.size)
        assertEquals(12, FolderColor.entries.map(FolderColor::id).distinct().size)
        assertTrue(FolderColor.entries.all { it.light != it.dark })
    }

    @Test fun unknownStoredColorStillFallsBackToBlue() {
        assertEquals(FolderColor.Blue, FolderColor.fromId("future-color"))
        assertEquals(FolderColor.Blue, FolderColor.fromId(null))
    }
}
