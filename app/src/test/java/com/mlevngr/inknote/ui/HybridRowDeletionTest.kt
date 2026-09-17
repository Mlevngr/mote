package com.mlevngr.inknote.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HybridRowDeletionTest {
    @Test fun eachCrossLineBackspaceRemovesOnlyTheConsumedPreviewRow() {
        val initial = listOf(
            markdown(0, "First"),
            markdown(1, "Second"),
            HybridRow.Editor(2, "Third"),
            markdown(3, "Fourth")
        )

        val first = HybridRowDeletion.apply(initial, 2, 1, 1, 1, "SecondThird")
        assertEquals(
            listOf(markdown(0, "First"), HybridRow.Editor(1, "SecondThird"), markdown(2, "Fourth")),
            first
        )

        val second = HybridRowDeletion.apply(first, 1, 0, 0, 1, "FirstSecondThird")
        assertEquals(
            listOf(HybridRow.Editor(0, "FirstSecondThird"), markdown(1, "Fourth")),
            second
        )
    }

    @Test fun deletingTheActiveImageKeepsTheEditorAndConsumesOnlyTheNextPreview() {
        val initial = listOf(
            markdown(0, "Before"),
            HybridRow.Editor(1, "![[asset:assets/photo.jpg|Photo]]"),
            markdown(2, "After"),
            markdown(3, "Later")
        )

        val result = HybridRowDeletion.apply(initial, 1, 1, 2, 1, "After")

        assertEquals(
            listOf(markdown(0, "Before"), HybridRow.Editor(1, "After"), markdown(2, "Later")),
            result
        )
    }

    @Test fun deletingSeveralLinesAtOnceRemovesExactlyThoseRows() {
        val initial = listOf(
            markdown(0, "Keep"),
            markdown(1, "Remove one"),
            markdown(2, "Remove two"),
            HybridRow.Editor(3, "Current"),
            markdown(4, "Keep later")
        )

        val result = HybridRowDeletion.apply(initial, 3, 1, 1, 2, "Combined")

        assertEquals(
            listOf(markdown(0, "Keep"), HybridRow.Editor(1, "Combined"), markdown(2, "Keep later")),
            result
        )
    }

    @Test fun deletingTheLastImageConsumesThePreviousPreviewImmediately() {
        val initial = listOf(
            markdown(0, "Before"),
            HybridRow.Editor(1, "![[asset:assets/photo.jpg|Photo]]")
        )

        val result = HybridRowDeletion.apply(initial, 1, 0, 0, 1, "Before")

        assertEquals(listOf(HybridRow.Editor(0, "Before")), result)
    }

    private fun markdown(index: Int, text: String) =
        HybridRow.Rendered(index, PreviewRow.Markdown(text))
}
