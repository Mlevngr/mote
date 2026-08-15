package com.mlevngr.inknote.markdown

sealed interface PreviewBlock {
    data class Markdown(val source: String) : PreviewBlock
    data class Image(val relativePath: String, val alt: String) : PreviewBlock
    data class Pdf(val relativePath: String, val label: String) : PreviewBlock
}
