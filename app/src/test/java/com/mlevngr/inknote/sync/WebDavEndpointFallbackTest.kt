package com.mlevngr.inknote.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WebDavEndpointFallbackTest {
    private val internal = WebDavEndpoint(WebDavEndpoint.Kind.Internal, "http://nas/")
    private val external = WebDavEndpoint(WebDavEndpoint.Kind.External, "https://dav.example/")

    @Test fun usesInternalEndpointWhenItIsAvailable() {
        val attempted = mutableListOf<WebDavEndpoint.Kind>()

        val result = WebDavEndpointFallback.run(listOf(internal, external)) {
            attempted += it.kind
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(listOf(WebDavEndpoint.Kind.Internal), attempted)
    }

    @Test fun fallsBackToExternalAfterInternalConnectionFailure() {
        val attempted = mutableListOf<WebDavEndpoint.Kind>()

        val result = WebDavEndpointFallback.run(listOf(internal, external)) {
            attempted += it.kind
            if (it.kind == WebDavEndpoint.Kind.Internal) throw java.io.IOException("offline")
            it.kind
        }

        assertEquals(WebDavEndpoint.Kind.External, result)
        assertEquals(listOf(WebDavEndpoint.Kind.Internal, WebDavEndpoint.Kind.External), attempted)
    }

    @Test fun doesNotRetrySharedInvalidCredentialsAgainstAnotherEndpoint() {
        val attempted = mutableListOf<WebDavEndpoint.Kind>()

        assertThrows(WebDavHttpException::class.java) {
            WebDavEndpointFallback.run(listOf(internal, external)) {
                attempted += it.kind
                throw WebDavHttpException(401, "unauthorized", retryable = false)
            }
        }

        assertEquals(listOf(WebDavEndpoint.Kind.Internal), attempted)
    }
}
