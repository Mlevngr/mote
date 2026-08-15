package com.mlevngr.inknote.ui

/** Exact insertion boundaries shown between logical Markdown lines. */
object InsertionTargetBoundaries {
    fun from(rows: List<HybridRow>): List<Int> = buildList {
        add(0)
        rows.forEachIndexed { index, row ->
            if (rows.getOrNull(index + 1)?.lineIndex != row.lineIndex) {
                add(row.lineIndex + 1)
            }
        }
    }
}
