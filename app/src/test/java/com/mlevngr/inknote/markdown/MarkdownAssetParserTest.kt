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

    @Test fun extractsAnImportedImage() {
        assertEquals(
            listOf(PreviewBlock.Image("assets/photo.png", "photo")),
            MarkdownAssetParser.parse("![photo](assets/photo.png)")
        )
    }

    @Test fun extractsPdfAsInlinePreviewBlock() {
        assertEquals(
            listOf(PreviewBlock.Pdf("assets/paper.pdf", "paper")),
            MarkdownAssetParser.parse("[paper](assets/paper.pdf)")
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
                PreviewBlock.Pdf("assets/b.pdf", "B"),
                PreviewBlock.Markdown("After")
            ),
            MarkdownAssetParser.parse(
                "Before\n![A](assets/a.jpg)\nBetween\n[B](assets/b.pdf)\nAfter"
            )
        )
    }
}
