package com.mlevngr.inknote.ui

sealed interface HybridRow {
    val lineIndex: Int

    data class Editor(
        override val lineIndex: Int,
        val source: String
    ) : HybridRow

    data class Rendered(
        override val lineIndex: Int,
        val preview: PreviewRow
    ) : HybridRow
}
