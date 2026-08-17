package com.mlevngr.inknote.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentTreePolicyTest {
    @Test fun detectsSameAndNestedDocumentTrees() {
        assertTrue(DocumentTreePolicy.isSameOrDescendant("files", "primary:Docs/Mote", "files", "primary:Docs/Mote"))
        assertTrue(DocumentTreePolicy.isSameOrDescendant("files", "primary:Docs/Mote/Backups", "files", "primary:Docs/Mote"))
    }

    @Test fun allowsSiblingAndDifferentProviders() {
        assertFalse(DocumentTreePolicy.isSameOrDescendant("files", "primary:Docs/Backups", "files", "primary:Docs/Mote"))
        assertFalse(DocumentTreePolicy.isSameOrDescendant("cloud", "primary:Docs/Mote", "files", "primary:Docs/Mote"))
    }
}
