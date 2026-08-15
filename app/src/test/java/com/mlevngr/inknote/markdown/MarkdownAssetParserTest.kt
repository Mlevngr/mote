package com.mlevngr.inknote.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownAssetParserTest {
    @Test fun keepsNormalMarkdownTogether() {
        assertEquals(
            listOf(PreviewBlock.Markdown("# Title\n\nA **bold** paragraph.")),
            MarkdownAssetParser.parse("# Title\n\nA **bold** paragraph.")
        )
    }

    @Test fun classifiesInkNoteEmbedsByCopiedFileType() {
        assertEquals(
            listOf(PreviewBlock.Image("assets/photo.png", "photo")),
            MarkdownAssetParser.parse("![[asset:assets/photo.png|photo]]")
        )
        assertEquals(
            listOf(PreviewBlock.Pdf("assets/paper.pdf", "paper")),
            MarkdownAssetParser.parse("![[asset:assets/paper.pdf|paper]]")
        )
        assertEquals(
            listOf(PreviewBlock.Attachment("assets/data.zip", "data")),
            MarkdownAssetParser.parse("![[asset:assets/data.zip|data]]")
        )
    }

    @Test fun keepsLegacyStandardMarkdownImportsWorking() {
        assertEquals(
            listOf(
                PreviewBlock.Image("assets/photo.png", "photo"),
                PreviewBlock.Pdf("assets/paper.pdf", "paper")
            ),
            MarkdownAssetParser.parse("![photo](assets/photo.png)\n[paper](assets/paper.pdf)")
        )
    }

    @Test fun keepsRemoteAndInlineLinksAsMarkdown() {
        val source = "Read [site](https://example.com) and ![remote](https://example.com/a.png)."
        assertEquals(listOf(PreviewBlock.Markdown(source)), MarkdownAssetParser.parse(source))
    }

    @Test fun preservesOrderAcrossMixedContent() {
        assertEquals(
            listOf(
                PreviewBlock.Markdown("Before"),
                PreviewBlock.Image("assets/a.jpg", "A"),
                PreviewBlock.Markdown("Between"),
                PreviewBlock.Attachment("assets/b.txt", "B"),
                PreviewBlock.Markdown("After")
            ),
            MarkdownAssetParser.parse(
                "Before\n![[asset:assets/a.jpg|A]]\nBetween\n![[asset:assets/b.txt|B]]\nAfter"
            )
        )
    }
}
