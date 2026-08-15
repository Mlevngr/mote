package com.mlevngr.inknote.ui

class HybridRowFactory(private val previewFactory: PreviewRowFactory) {
    fun create(lines: List<String>, activeLine: Int?): List<HybridRow> = buildList {
        lines.forEachIndexed { index, source ->
            if (index == activeLine) {
                add(HybridRow.Editor(index, source))
            } else {
                val previews = previewFactory.create(source)
                if (previews.isEmpty()) {
                    add(HybridRow.Rendered(index, PreviewRow.Markdown("\u00a0")))
                } else {
                    previews.forEach { add(HybridRow.Rendered(index, it)) }
                }
            }
        }
    }
}
