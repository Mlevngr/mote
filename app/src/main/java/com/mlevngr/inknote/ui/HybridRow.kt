package com.mlevngr.inknote.ui

sealed interface HybridRow {
    val blockIndex: Int

    data class Editor(
        override val blockIndex: Int,
        val source: String
    ) : HybridRow

    data class Rendered(
        override val blockIndex: Int,
        val preview: PreviewRow
    ) : HybridRow
}
