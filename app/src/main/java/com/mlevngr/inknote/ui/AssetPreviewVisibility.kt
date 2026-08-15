package com.mlevngr.inknote.ui

import java.io.File

object AssetPreviewVisibility {
    fun assetFile(row: HybridRow): File? = when (val preview = (row as? HybridRow.Rendered)?.preview) {
        is PreviewRow.Image -> preview.file
        is PreviewRow.PdfPage -> preview.file
        else -> null
    }

    fun visibleRows(rows: List<HybridRow>, collapsedAssetPaths: Set<String>): List<HybridRow> =
        rows.filter { row ->
            val preview = (row as? HybridRow.Rendered)?.preview
            preview !is PreviewRow.PdfPage ||
                preview.file.canonicalPath !in collapsedAssetPaths ||
                preview.pageIndex == 0
        }
}
