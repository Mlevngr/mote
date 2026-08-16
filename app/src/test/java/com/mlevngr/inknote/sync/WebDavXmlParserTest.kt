package com.mlevngr.inknote.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class WebDavXmlParserTest {
    @Test fun parsesNamespacedCollectionsAndFiles() {
        val xml = """<?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response><d:href>/dav/Mote/</d:href><d:propstat><d:prop>
                <d:resourcetype><d:collection/></d:resourcetype>
              </d:prop></d:propstat></d:response>
              <d:response><d:href>/dav/Mote/Note.note/note.md</d:href><d:propstat><d:prop>
                <d:resourcetype/><d:getetag>&quot;v2&quot;</d:getetag>
                <d:getcontentlength>12</d:getcontentlength>
                <d:getlastmodified>Sun, 06 Nov 1994 08:49:37 GMT</d:getlastmodified>
              </d:prop></d:propstat></d:response>
            </d:multistatus>
        """.trimIndent()

        val parsed = WebDavXmlParser.parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals(2, parsed.size)
        assertTrue(parsed[0].collection)
        assertFalse(parsed[1].collection)
        assertEquals("\"v2\"", parsed[1].etag)
        assertEquals(12, parsed[1].size)
    }

    @Test fun rejectsDocumentTypeDeclarations() {
        val xml = """<?xml version="1.0"?>
            <!DOCTYPE x [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
            <multistatus xmlns="DAV:"><response><href>&secret;</href></response></multistatus>
        """.trimIndent()

        assertThrows(Exception::class.java) {
            WebDavXmlParser.parse(ByteArrayInputStream(xml.toByteArray()))
        }
    }
}
