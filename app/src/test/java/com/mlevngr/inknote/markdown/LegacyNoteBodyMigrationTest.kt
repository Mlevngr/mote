package com.mlevngr.inknote.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyNoteBodyMigrationTest {
    @Test fun removesTheGeneratedTitleAndItsSeparatorFromLegacyBody() {
        assertEquals(
            "First paragraph.\n\nSecond paragraph.",
            LegacyNoteBodyMigration.separateTitle(
                "# Meeting\n\nFirst paragraph.\n\nSecond paragraph.",
                "Meeting"
            )
        )
    }

    @Test fun keepsAHeadingThatDoesNotMatchTheFileTitle() {
        val markdown = "# Section heading\n\nBody"
        assertEquals(markdown, LegacyNoteBodyMigration.separateTitle(markdown, "Meeting"))
    }

    @Test fun turnsATitleOnlyLegacyNoteIntoAnEmptyBody() {
        assertEquals("", LegacyNoteBodyMigration.separateTitle("# Meeting\n", "Meeting"))
    }
}
