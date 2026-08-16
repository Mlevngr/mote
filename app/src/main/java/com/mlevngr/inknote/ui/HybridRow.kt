package com.mlevngr.inknote.ui

sealed interface HybridRow {
    val lineIndex: Int
    val pdfContext: PdfRowContext?

    data class Editor(
        override val lineIndex: Int,
        val source: String,
        override val pdfContext: PdfRowContext? = null
    ) : HybridRow

    data class Rendered(
        override val lineIndex: Int,
        val preview: PreviewRow,
        override val pdfContext: PdfRowContext? = null
    ) : HybridRow
}

data class PdfRowContext(
    val instanceKey: String,
    val assetLineIndex: Int,
    val pageIndex: Int
)
