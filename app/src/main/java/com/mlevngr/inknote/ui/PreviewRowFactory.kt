package com.mlevngr.inknote.ui

import com.mlevngr.inknote.assets.NoteWorkspace
import com.mlevngr.inknote.markdown.MarkdownAssetParser
import com.mlevngr.inknote.markdown.PreviewBlock
import com.mlevngr.inknote.pdf.PdfDocumentSource

class PreviewRowFactory(private val workspace: NoteWorkspace) {
    fun create(markdown: String): List<PreviewRow> = MarkdownAssetParser.parse(markdown).flatMap { block ->
        when (block) {
            is PreviewBlock.Markdown -> listOf(PreviewRow.Markdown(block.source))
            is PreviewBlock.Image -> {
                val file = workspace.resolveAsset(block.relativePath)
                if (file == null) {
                    listOf(PreviewRow.Error("Missing image: ${block.relativePath}"))
                } else {
                    listOf(PreviewRow.Image(file, block.alt))
                }
            }
            is PreviewBlock.Pdf -> {
                val file = workspace.resolveAsset(block.relativePath)
                if (file == null) {
                    listOf(PreviewRow.Error("Missing PDF: ${block.relativePath}"))
                } else {
                    runCatching {
                        PdfDocumentSource(file).use { source ->
                            List(source.pageCount) { page ->
                                PreviewRow.PdfPage(
                                    file = file,
                                    label = block.label.ifBlank { file.name },
                                    pageIndex = page,
                                    pageCount = source.pageCount
                                )
                            }
                        }
                    }.getOrElse {
                        listOf(PreviewRow.Error("Cannot preview PDF: ${block.label}"))
                    }
                }
            }
        }
    }
}
