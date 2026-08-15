package com.mlevngr.inknote.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AssetPathPolicyTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun resolvesAssetInsideNote() {
        val note = temporaryFolder.newFolder("note")
        assertEquals(
            File(note, "assets/image.png").canonicalFile,
            AssetPathPolicy.resolve(note, "assets/image.png")
        )
    }

    @Test fun rejectsTraversalOutsideAssets() {
        val note = temporaryFolder.newFolder("note")
        assertNull(AssetPathPolicy.resolve(note, "assets/../note.md"))
        assertNull(AssetPathPolicy.resolve(note, "../secret.pdf"))
    }

    @Test fun rejectsAbsoluteAndLookalikePaths() {
        val note = temporaryFolder.newFolder("note")
        assertNull(AssetPathPolicy.resolve(note, "/assets/image.png"))
        assertNull(AssetPathPolicy.resolve(note, "assets-other/image.png"))
    }
}
