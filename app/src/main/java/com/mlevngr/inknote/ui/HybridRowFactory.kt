package com.mlevngr.inknote.ui

class HybridRowFactory(private val previewFactory: PreviewRowFactory) {
    fun create(blocks: List<String>, activeBlock: Int?): List<HybridRow> = buildList {
        blocks.forEachIndexed { index, source ->
            if (index == activeBlock) {
                add(HybridRow.Editor(index, source))
            } else {
                val previews = previewFactory.create(source)
                if (previews.isEmpty()) {
                    add(HybridRow.Rendered(index, PreviewRow.Markdown("点击开始书写…")))
                } else {
                    previews.forEach { add(HybridRow.Rendered(index, it)) }
                }
            }
        }
    }
}
