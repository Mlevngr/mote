package com.mlevngr.inknote.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownDocumentTest {
    @Test fun preservesEveryMarkdownLineIncludingBlankLines() {
        val source = "# Title\n\nFirst paragraph.\n- one\n- two"
        val document = MarkdownDocument.parse(source)
        assertEquals(listOf("# Title", "", "First paragraph.", "- one", "- two"), document.snapshot())
        assertEquals(source, document.markdown())
    }

    @Test fun preservesTrailingBlankLine() {
        val document = MarkdownDocument.parse("First\n")
        assertEquals(listOf("First", ""), document.snapshot())
        assertEquals("First\n", document.markdown())
    }

    @Test fun updatesAndInsertsIndividualLines() {
        val document = MarkdownDocument.parse("First\nThird")
        document.update(0, "Changed")
        assertEquals(1, document.insertAfter(0, "Second"))
        assertEquals("Changed\nSecond\nThird", document.markdown())
    }

    @Test fun togglesTaskCompletionInBothDirections() {
        val document = MarkdownDocument.parse("- [ ] Buy milk")

        assertEquals(true, document.toggleTask(0))
        assertEquals("- [x] Buy milk", document.markdown())

        assertEquals(true, document.toggleTask(0))
        assertEquals("- [ ] Buy milk", document.markdown())
    }

    @Test fun togglingTaskPreservesMarkerIndentationAndContent() {
        val document = MarkdownDocument.parse("  + [X] **Nested task**")

        assertEquals(true, document.toggleTask(0))

        assertEquals("  + [ ] **Nested task**", document.markdown())
    }

    @Test fun refusesToToggleANonTaskLine() {
        val document = MarkdownDocument.parse("- ordinary list item")

        assertEquals(false, document.toggleTask(0))
        assertEquals("- ordinary list item", document.markdown())
    }

    @Test fun splitsAtCursorAndReturnsNewActiveLine() {
        val document = MarkdownDocument.parse("Hello world")
        assertEquals(1, document.splitLine(0, 5))
        assertEquals(listOf("Hello", " world"), document.snapshot())
    }

    @Test fun renumbersTheOrderedSequenceAroundAnInsertedLine() {
        val document = MarkdownDocument.parse("1. First\n1. Inserted\n2. Second\n3. Third")

        assertEquals(true, document.renumberOrderedListAt(1))
        assertEquals("1. First\n2. Inserted\n3. Second\n4. Third", document.markdown())
    }

    @Test fun renumbersFollowingItemsFromADeletedItemsNumber() {
        val document = MarkdownDocument.parse("1. First\nplain\n3. Third\n4. Fourth")

        assertEquals(true, document.renumberOrderedListAt(2, startingNumber = 2))
        assertEquals("1. First\nplain\n2. Third\n3. Fourth", document.markdown())
    }

    @Test fun restoringADeletedMarkerRejoinsAndRenumbersTheList() {
        val document = MarkdownDocument.parse("1. First\n. Restored\n2. Second\n3. Third")

        val update = document.updateWithOrderedListReconciliation(1, "2. Restored", 1, 1)

        assertEquals(true, update.renumbered)
        assertEquals("1. First\n2. Restored\n3. Second\n4. Third", document.markdown())
        assertEquals(1, update.edit.selectionStart)
        assertEquals(1, update.edit.selectionEnd)
    }

    @Test fun deletingAndRestoringAnOrderedMarkerWorksInBothDirections() {
        val document = MarkdownDocument.parse("1. First\n2. Middle\n3. Third\n4. Fourth")

        document.updateWithOrderedListReconciliation(1, ". Middle", 0, 0)
        assertEquals("1. First\n. Middle\n2. Third\n3. Fourth", document.markdown())

        document.updateWithOrderedListReconciliation(1, "2. Middle", 1, 1)
        assertEquals("1. First\n2. Middle\n3. Third\n4. Fourth", document.markdown())
    }

    @Test fun changingAMiddleItemNumberNormalizesTheWholeRun() {
        val document = MarkdownDocument.parse("1. First\n2. Middle\n3. Third")

        val update = document.updateWithOrderedListReconciliation(1, "9. Middle", 1, 1)

        assertEquals(true, update.renumbered)
        assertEquals("1. First\n2. Middle\n3. Third", document.markdown())
        assertEquals(3, update.edit.selectionStart)
    }

    @Test fun changingOnlyListContentDoesNotRenumberOrRefresh() {
        val document = MarkdownDocument.parse("1. First\n2. Middle\n3. Third")

        val update = document.updateWithOrderedListReconciliation(1, "2. Changed", 10, 10)

        assertEquals(false, update.renumbered)
        assertEquals("1. First\n2. Changed\n3. Third", document.markdown())
    }

    @Test fun changingIndentRenumbersTheOldRunWithoutTouchingTheNestedRun() {
        val document = MarkdownDocument.parse("1. First\n2. Middle\n3. Third\n4. Fourth")

        document.updateWithOrderedListReconciliation(1, "  1. Nested", 5, 5)

        assertEquals("1. First\n  1. Nested\n2. Third\n3. Fourth", document.markdown())
    }

    @Test fun backspaceAtLineStartMergesWithPreviousLine() {
        val document = MarkdownDocument.parse("Hello\n world")
        assertEquals(5, document.mergeWithPrevious(1))
        assertEquals(listOf("Hello world"), document.snapshot())
        assertEquals(null, document.mergeWithPrevious(0))
    }

    @Test fun longBackspaceCrossesLineBoundaryWithoutStoppingAtPreviousEnd() {
        val document = MarkdownDocument.parse("First line\nSecond line")

        val result = document.deleteBackwardAtLineStart(1, 6)

        assertEquals(BackwardDeleteResult(0, 5), result)
        assertEquals("FirstSecond line", document.markdown())
    }

    @Test fun backspaceBeforeAnImageRemovesTheWholeEmbed() {
        val document = MarkdownDocument.parse("Before\n![[asset:assets/photo.jpg|Photo]]\nAfter")

        val result = document.deleteBackwardAtLineStart(2, 1)

        assertEquals(BackwardDeleteResult(1, 0, listOf("assets/photo.jpg")), result)
        assertEquals("Before\nAfter", document.markdown())
    }

    @Test fun deletingAnActiveImageRemovesItsWholeSourceLine() {
        val document = MarkdownDocument.parse("Before\n![Photo](assets/photo.jpg)\nAfter")

        val result = document.deleteImageLine(1)

        assertEquals(BackwardDeleteResult(1, 0, listOf("assets/photo.jpg")), result)
        assertEquals("Before\nAfter", document.markdown())
    }

    @Test fun deletingTheOnlyImageLeavesAnEditableEmptyDocument() {
        val document = MarkdownDocument.parse("![[asset:assets/photo.jpg|Photo]]")

        val result = document.deleteImageLine(0)

        assertEquals(BackwardDeleteResult(0, 0, listOf("assets/photo.jpg")), result)
        assertEquals("", document.markdown())
    }

    @Test fun longBackspaceCanRemoveAnImageAndContinueIntoEarlierText() {
        val document = MarkdownDocument.parse("Before\n![[asset:assets/photo.jpg|Photo]]\nAfter")

        val result = document.deleteBackwardAtLineStart(2, 4)

        assertEquals(BackwardDeleteResult(0, 4, listOf("assets/photo.jpg")), result)
        assertEquals("BefoAfter", document.markdown())
    }

    @Test fun codePointBackspaceDoesNotSplitAnEmojiAcrossLines() {
        val document = MarkdownDocument.parse("A😀\nNext")

        val result = document.deleteBackwardAtLineStart(1, 2, byCodePoints = true)

        assertEquals(BackwardDeleteResult(0, 1), result)
        assertEquals("ANext", document.markdown())
    }

    @Test fun backspaceDoesNotCrossPdfPageNoteMarkers() {
        val document = MarkdownDocument.parse(
            "![[asset:assets/paper.pdf|Paper|mote-id:one]]\n" +
                "<!-- mote:pdf-note:one:0 -->\nNote\n<!-- /mote:pdf-note:one -->\nAfter"
        )

        assertEquals(null, document.deleteBackwardAtLineStart(4, 1))
        assertEquals("After", document[4])
    }

    @Test fun backspaceCannotMergeTheFirstBodyLineIntoTheSeparateTitle() {
        val document = MarkdownDocument.parse("Body")
        assertEquals(null, document.mergeWithPrevious(0))
        assertEquals("Body", document.markdown())
    }

    @Test fun replacesOneEditorLineWithPastedLines() {
        val document = MarkdownDocument.parse("Before\nOld\nAfter")
        assertEquals(1..3, document.replaceLine(1, listOf("One", "Two", "Three")))
        assertEquals("Before\nOne\nTwo\nThree\nAfter", document.markdown())
    }

    @Test fun emptyDocumentStillHasAnEditableLine() {
        assertEquals(listOf(""), MarkdownDocument.parse("").snapshot())
    }

    @Test fun removesAnEmbeddedAssetLineWithoutCrossingIntoOtherLines() {
        val document = MarkdownDocument.parse("Before\n![[asset:assets/photo.jpg|Photo]]\nAfter")
        document.removeLine(1)
        assertEquals("Before\nAfter", document.markdown())
    }

    @Test fun copiesAtAnExactBoundaryWithoutReplacingNearbyText() {
        val document = MarkdownDocument.parse("Before\nAfter")

        assertEquals(1..1, document.pasteAtInsertion(1, "Asset"))
        assertEquals(listOf("Before", "Asset", "After"), document.snapshot())
    }

    @Test fun cutThenPasteUsesRemoveAndExactInsertionWithoutLeavingACopy() {
        val document = MarkdownDocument.parse("Before\nAsset\nMiddle\nAfter")
        val cut = document[1]

        document.removeLine(1)
        assertEquals(2..2, document.pasteAtInsertion(2, cut))

        assertEquals(listOf("Before", "Middle", "Asset", "After"), document.snapshot())
    }

    @Test fun removingTheOnlyLineLeavesAnEditableEmptyBody() {
        val document = MarkdownDocument.parse("Only line")
        document.removeLine(0)
        assertEquals(listOf(""), document.snapshot())
    }

    @Test fun pastesIntoAnExistingBlankLine() {
        val document = MarkdownDocument.parse("Before\n\nAfter")
        assertEquals(1..1, document.pasteAt(1, "Pasted"))
        assertEquals("Before\nPasted\nAfter", document.markdown())
    }

    @Test fun pastesAfterANonBlankBlock() {
        val document = MarkdownDocument.parse("Before\nAfter")
        assertEquals(1..2, document.pasteAt(0, "One\nTwo"))
        assertEquals("Before\nOne\nTwo\nAfter", document.markdown())
    }

    @Test fun pastesIntoAnEmptyDocumentWithoutLeavingAnExtraLine() {
        val document = MarkdownDocument.parse("")
        assertEquals(0..0, document.pasteAt(null, "Pasted"))
        assertEquals("Pasted", document.markdown())
    }
}
