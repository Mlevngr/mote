package com.mlevngr.inknote.ui

object AssetPreviewVisibility {
    data class AssetInstanceKey(val identity: String) {
        constructor(lineIndex: Int, canonicalPath: String) : this("legacy:$lineIndex:$canonicalPath")
    }

    fun assetKey(row: HybridRow): AssetInstanceKey? =
        when (val preview = (row as? HybridRow.Rendered)?.preview) {
            is PreviewRow.Image -> AssetInstanceKey(row.lineIndex, preview.file.canonicalPath)
            is PreviewRow.PdfPage -> AssetInstanceKey(
                preview.instanceKey ?: "legacy:${row.lineIndex}:${preview.file.canonicalPath}"
            )
            else -> null
        }

    fun visibleRows(
        rows: List<HybridRow>,
        collapsedAssets: Set<AssetInstanceKey>,
    ): List<HybridRow> =
        rows.filter { row ->
            val preview = (row as? HybridRow.Rendered)?.preview
            val contextKey = row.pdfContext?.let { AssetInstanceKey(it.instanceKey) }
            when {
                contextKey != null && contextKey in collapsedAssets ->
                    preview is PreviewRow.PdfPage &&
                        assetKey(row) == contextKey &&
                        preview.pageIndex == 0
                preview is PreviewRow.PdfPage ->
                    assetKey(row) !in collapsedAssets || preview.pageIndex == 0
                else -> true
            }
        }
}

object PdfPreviewVisibility {
    data class PageKey(val instanceKey: String, val pageIndex: Int)

    fun isPageExpanded(page: PageKey, collapsedPages: Set<PageKey>): Boolean =
        page !in collapsedPages
}
