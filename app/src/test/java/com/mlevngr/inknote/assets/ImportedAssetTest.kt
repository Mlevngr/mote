package com.mlevngr.inknote.assets

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportedAssetTest {
    @Test fun createsPortableMarkdownForImagesAndPdf() {
        assertEquals(
            "![photo](assets/id.png)",
            ImportedAsset("assets/id.png", "photo", ImportedAsset.Kind.Image).markdown()
        )
        assertEquals(
            "[paper](assets/id.pdf)",
            ImportedAsset("assets/id.pdf", "paper", ImportedAsset.Kind.Pdf).markdown()
        )
    }

    @Test fun sanitizesLabelsThatWouldBreakMarkdown() {
        assertEquals(
            "![(draft) name](assets/id.png)",
            ImportedAsset("assets/id.png", "[draft]\nname", ImportedAsset.Kind.Image).markdown()
        )
    }
}
