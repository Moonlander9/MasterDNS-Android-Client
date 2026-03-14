package com.masterdnsvpn.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteProfileRequestTest {

    @Test
    fun `buildRemoteProfileRequest defaults bare host to http`() {
        val result = buildRemoteProfileRequest(
            server = "192.168.1.10:8080",
            profileName = "jack",
        )

        assertTrue(result.isSuccess)
        val request = result.getOrThrow()
        assertEquals("http://192.168.1.10:8080/jack", request.url.toString())
        assertEquals("/jack", request.path)
    }

    @Test
    fun `buildRemoteProfileRequest preserves https and base path`() {
        val result = buildRemoteProfileRequest(
            server = "https://example.com/profiles/v1/",
            profileName = "alpha beta",
        )

        assertTrue(result.isSuccess)
        val request = result.getOrThrow()
        assertEquals("https://example.com/profiles/v1/alpha%20beta", request.url.toString())
        assertEquals("/profiles/v1/alpha%20beta", request.path)
    }

    @Test
    fun `buildRemoteProfileRequest rejects unsupported schemes`() {
        val result = buildRemoteProfileRequest(
            server = "ftp://example.com",
            profileName = "jack",
        )

        assertTrue(result.isFailure)
        assertEquals("Server must use http:// or https://", result.exceptionOrNull()?.message)
    }
}
