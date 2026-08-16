package com.mlevngr.inknote.ui

import java.io.File

sealed interface PreviewRow {
    data class Markdown(val source: String) : PreviewRow
    data class Image(val file: File, val label: String) : PreviewRow
    data class PdfPage(
        val file: File,
        val label: String,
        val pageIndex: Int,
        val pageCount: Int,
        val instanceKey: String? = null
    ) : PreviewRow
    data class Attachment(val file: File, val label: String) : PreviewRow
    data class Error(val message: String) : PreviewRow
}
