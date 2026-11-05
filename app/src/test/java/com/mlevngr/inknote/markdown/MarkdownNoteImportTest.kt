package com.mlevngr.inknote.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class MarkdownNoteImportTest {
    @Test fun derivesNamesFromMarkdownExtensionsWithoutChangingUnicode() {
        assertEquals("阅读笔记", MarkdownNoteImport.noteName("阅读笔记.MD"))
        assertEquals("my.note", MarkdownNoteImport.noteName("my.note.markdown"))
        assertEquals("导入笔记", MarkdownNoteImport.noteName(".md"))
        assertThrows(IllegalArgumentException::class.java) { MarkdownNoteImport.noteName("image.png") }
    }

    @Test fun preservesHeadingsWhitespaceAndLineEndings() {
        val markdown = "# 阅读笔记\r\n\r\n- [ ] 待办  \r\n\n```kotlin\nval x = 1\n```\n"
        assertEquals(markdown, MarkdownNoteImport.read(markdown.byteInputStream()))
        assertEquals("", MarkdownNoteImport.read("".byteInputStream()))
    }

    @Test fun decodesUtf8AndUtf16BomWithoutKeepingTheBom() {
        val markdown = "# 中文 😀\n正文"
        listOf(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) to Charsets.UTF_8,
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()) to Charsets.UTF_16LE,
            byteArrayOf(0xFE.toByte(), 0xFF.toByte()) to Charsets.UTF_16BE
        ).forEach { (bom, charset) ->
            assertEquals(markdown, MarkdownNoteImport.read(ByteArrayInputStream(bom + markdown.toByteArray(charset))))
        }
    }

    @Test fun rejectsBinaryOrMalformedTextInsteadOfSilentlyCorruptingIt() {
        listOf(byteArrayOf(0, 1, 2), byteArrayOf(0xC3.toByte(), 0x28)).forEach { bytes ->
            assertThrows(IllegalArgumentException::class.java) {
                MarkdownNoteImport.read(ByteArrayInputStream(bytes))
            }
        }
    }

    @Test fun rejectsOversizedStreamsWhileReading() {
        val source = object : InputStream() {
            override fun read(): Int = 'a'.code
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                buffer.fill('a'.code.toByte(), offset, offset + length)
                return length
            }
        }
        assertThrows(IllegalArgumentException::class.java) { MarkdownNoteImport.read(source) }
    }

    @Test fun propagatesDocumentProviderReadFailure() {
        val source = object : InputStream() {
            override fun read(): Int = throw java.io.IOException("Document provider unavailable")
        }
        assertThrows(java.io.IOException::class.java) { MarkdownNoteImport.read(source) }
    }
}
