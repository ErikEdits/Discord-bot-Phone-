package de.erikedits.pocketbot.shared

import de.erikedits.pocketbot.shared.util.Pkce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {
    @Test
    fun `challenge matches RFC 7636 example`() {
        // Appendix B of RFC 7636
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `verifier has allowed length and is random`() {
        val a = Pkce.newVerifier()
        val b = Pkce.newVerifier()
        assertTrue(a.length in 43..128)
        assertTrue(a.matches(Regex("^[A-Za-z0-9_-]+$")))
        assertNotEquals(a, b)
    }
}
