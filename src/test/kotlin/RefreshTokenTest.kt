package com.vetplatform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RefreshTokenTest {
    @Test
    fun `refresh tokens are opaque url safe and independently random`() {
        val first = JwtService.generateRefreshToken()
        val second = JwtService.generateRefreshToken()

        assertEquals(43, first.length)
        assertTrue(first.matches(Regex("^[A-Za-z0-9_-]+$")))
        assertNotEquals(first, second)
    }

    @Test
    fun `refresh token hashing uses sha256 without returning plaintext`() {
        val hash = JwtService.hashRefreshToken("abc")

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash)
        assertEquals(64, hash.length)
        assertFalse(hash.contains("abc"))
    }
}
