package com.mlevngr.inknote.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WebDavConfigTest {
    @Test fun internalAndExternalEndpointsAreNormalizedInPriorityOrder() {
        val config = WebDavConfig(
            internalUrl = " http://192.168.1.10/dav/ ",
            externalUrl = "https://dav.example.com/root",
            remoteFolder = " Notes / Mote ",
            username = " user ",
            password = "secret"
        ).validated()

        assertEquals("http://192.168.1.10/dav/", config.internalUrl)
        assertEquals("https://dav.example.com/root/", config.externalUrl)
        assertEquals("Notes/Mote", config.remoteFolder)
        assertEquals(WebDavEndpoint.Kind.Internal, config.endpoints().first().kind)
        assertEquals("user", config.username)
    }

    @Test fun externalEndpointRequiresHttps() {
        assertThrows(IllegalArgumentException::class.java) {
            WebDavConfig("", "http://dav.example.com", "Mote", "", "").validated()
        }
    }

    @Test fun internalHttpEndpointIsAllowedForLanServers() {
        val config = WebDavConfig("http://nas.local/dav", "", "", "", "").validated()
        assertEquals(WebDavConfig.DEFAULT_REMOTE_FOLDER, config.remoteFolder)
    }

    @Test fun atLeastOneEndpointIsRequired() {
        assertThrows(IllegalArgumentException::class.java) {
            WebDavConfig("", "", "Mote", "", "").validated()
        }
    }

    @Test fun remoteFolderCannotEscapeItsCollection() {
        assertThrows(IllegalArgumentException::class.java) {
            WebDavConfig("https://dav.example.com", "", "../Mote", "", "").validated()
        }
    }

    @Test fun changingAnEndpointInvalidatesThePreviousSyncStateIdentity() {
        val first = WebDavConfig("http://nas-a/dav", "", "Mote", "user", "secret").validated()
        val second = first.copy(internalUrl = "http://nas-b/dav/")

        org.junit.Assert.assertNotEquals(first.stateIdentity, second.stateIdentity)
    }
}
