package com.mlevngr.inknote.assets

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportedAssetTest {
    @Test fun createsNonLinkEmbedsForEveryFileType() {
        assertEquals(
            "![[asset:assets/id.png|photo]]",
            ImportedAsset("assets/id.png", "photo", ImportedAsset.Kind.Image).markdown()
        )
        assertEquals(
            "![[asset:assets/id.pdf|paper]]",
            ImportedAsset("assets/id.pdf", "paper", ImportedAsset.Kind.Pdf).markdown()
        )
        assertEquals(
            "![[asset:assets/id.zip|archive]]",
            ImportedAsset("assets/id.zip", "archive", ImportedAsset.Kind.Attachment).markdown()
        )
    }

    @Test fun sanitizesLabelsThatWouldBreakEmbedSyntax() {
        assertEquals(
            "![[asset:assets/id.png|(draft) name]]",
            ImportedAsset("assets/id.png", "[draft]\nname", ImportedAsset.Kind.Image).markdown()
        )
        assertEquals(
            "![[asset:assets/id.bin|a b)]]",
            ImportedAsset("assets/id.bin", "a|b]", ImportedAsset.Kind.Attachment).markdown()
        )
    }
}
