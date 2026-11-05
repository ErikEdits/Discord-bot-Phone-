package de.erikedits.pocketbot.shared.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** PKCE helper (RFC 7636, S256) for the login between app and backend. */
object Pkce {
    private val random = SecureRandom()

    fun randomUrlSafe(bytes: Int = 32): String {
        val buf = ByteArray(bytes).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf)
    }

    /** 43 characters, within the 43–128 the RFC allows. */
    fun newVerifier(): String = randomUrlSafe(32)

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
