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

/** Updates the visible model without replacing the focused editor during repeated Backspace. */
object HybridRowDeletion {
    fun apply(
        rows: List<HybridRow>,
        oldEditorLine: Int,
        newEditorLine: Int,
        removedLineStart: Int,
        removedLineCount: Int,
        newSource: String
    ): List<HybridRow> {
        val removed = removedLineStart until removedLineStart + removedLineCount
        fun shifted(index: Int) = index - (index - removedLineStart).coerceIn(0, removedLineCount)
        return rows.mapNotNull { row ->
            if (row.lineIndex in removed) return@mapNotNull null
            val context = row.pdfContext?.let { pdf ->
                pdf.copy(assetLineIndex = shifted(pdf.assetLineIndex))
            }
            when (row) {
                is HybridRow.Editor -> row.copy(
                    lineIndex = if (row.lineIndex == oldEditorLine) newEditorLine
                    else shifted(row.lineIndex),
                    source = if (row.lineIndex == oldEditorLine) newSource else row.source,
                    pdfContext = context
                )
                is HybridRow.Rendered -> row.copy(
                    lineIndex = shifted(row.lineIndex),
                    pdfContext = context
                )
            }
        }
    }
}
