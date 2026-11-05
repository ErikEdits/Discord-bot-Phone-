package de.erikedits.pocketbot.shared

import de.erikedits.pocketbot.shared.vault.VaultCrypto
import de.erikedits.pocketbot.shared.vault.VaultFormatException
import de.erikedits.pocketbot.shared.vault.VaultSecret
import de.erikedits.pocketbot.shared.vault.WrongPinException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class VaultCryptoTest {
    private val secret = VaultSecret(token = "x".repeat(70), name = "TestBot", avatarUrl = "https://cdn.discordapp.com/embed/avatars/1.png")

    @Test
    fun `round trip with the right pin`() {
        val blob = VaultCrypto.encrypt(secret, "123456", memoryKb = 1024, iterations = 1)
        assertEquals(secret, VaultCrypto.decrypt(blob, "123456"))
    }

    @Test(expected = WrongPinException::class)
    fun `wrong pin is detected`() {
        val blob = VaultCrypto.encrypt(secret, "123456", memoryKb = 1024, iterations = 1)
        VaultCrypto.decrypt(blob, "654321")
    }

    @Test
    fun `blob hides the token and is random each time`() {
        val a = VaultCrypto.encrypt(secret, "123456", memoryKb = 1024, iterations = 1)
        val b = VaultCrypto.encrypt(secret, "123456", memoryKb = 1024, iterations = 1)
        assertNotEquals(a, b)
        val decoded = String(Base64.getDecoder().decode(a))
        assertFalse(decoded.contains("xxxx"))
        assertTrue(decoded.contains("\"kdf\":\"argon2id\""))
        assertTrue("blob must stay under the 8 KB server limit", Base64.getDecoder().decode(a).size < 8 * 1024)
    }

    @Test
    fun `default parameters work`() {
        val blob = VaultCrypto.encrypt(secret, "geheim123")
        assertEquals(secret, VaultCrypto.decrypt(blob, "geheim123"))
    }

    @Test(expected = VaultFormatException::class)
    fun `garbage blob`() {
        VaultCrypto.decrypt("bm90LWpzb24=", "123456")
    }

    @Test
    fun `pin rules`() {
        assertFalse(VaultCrypto.isValidPin("12345"))
        assertTrue(VaultCrypto.isValidPin("123456"))
    }
}
