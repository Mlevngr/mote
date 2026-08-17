package com.mlevngr.inknote.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AtomicFileWriterTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun replacesExistingTextWithoutLeavingTemporaryFiles() {
        val directory = temporaryFolder.newFolder("note.note")
        val target = File(directory, "note.md").apply { writeText("old") }

        AtomicFileWriter.writeText(target, "新的正文 📝")

        assertEquals("新的正文 📝", target.readText())
        assertFalse(directory.listFiles().orEmpty().any { it.name.startsWith("note.md.tmp-") })
    }

    @Test fun failedWriteKeepsExistingContentAndRemovesTemporaryFile() {
        val directory = temporaryFolder.newFolder("failed-note.note")
        val target = File(directory, "note.md").apply { writeText("safe original") }

        val failure = runCatching {
            AtomicFileWriter.write(target) { output ->
                output.write("partial".toByteArray())
                error("simulated crash")
            }
        }.exceptionOrNull()

        assertEquals("simulated crash", failure?.message)
        assertEquals("safe original", target.readText())
        assertFalse(directory.listFiles().orEmpty().any { it.name.startsWith("note.md.tmp-") })
    }
}
