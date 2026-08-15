package com.mlevngr.inknote.ui

object AssetPreviewVisibility {
    data class AssetInstanceKey(val lineIndex: Int, val canonicalPath: String)

    fun assetKey(row: HybridRow): AssetInstanceKey? =
        when (val preview = (row as? HybridRow.Rendered)?.preview) {
            is PreviewRow.Image -> AssetInstanceKey(row.lineIndex, preview.file.canonicalPath)
            is PreviewRow.PdfPage -> AssetInstanceKey(row.lineIndex, preview.file.canonicalPath)
            else -> null
        }

    fun visibleRows(
        rows: List<HybridRow>,
        collapsedAssets: Set<AssetInstanceKey>,
    ): List<HybridRow> =
        rows.filter { row ->
            val preview = (row as? HybridRow.Rendered)?.preview
            preview !is PreviewRow.PdfPage ||
                assetKey(row) !in collapsedAssets ||
                preview.pageIndex == 0
        }
}
