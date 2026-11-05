package de.erikedits.pocketbot.shared.vault

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** What is stored for one bot in the cloud vault (encrypted before it leaves the phone). */
@Serializable
data class VaultSecret(
    val token: String,
    val name: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/** The opaque blob the server stores (docs/BACKEND-INTEGRATION.md 4.3). */
@Serializable
internal data class VaultEnvelope(
    val v: Int = 1,
    val kdf: String = "argon2id",
    val m: Int,
    val t: Int,
    val p: Int,
    val salt: String,
    val nonce: String,
    val ct: String,
)

class WrongPinException : Exception("Wrong PIN")
class VaultFormatException(message: String) : Exception(message)

/**
 * End-to-end encryption for the cloud vault (SPEC 6.2): key = Argon2id(PIN, random salt),
 * cipher = AES-256-GCM. The server only ever sees the base64 blob and cannot decrypt it.
 */
object VaultCrypto {
    // OWASP minimum for Argon2id: 19 MiB, 2 iterations, 1 lane. Stored in each blob, so it can change later.
    const val MEMORY_KB = 19 * 1024
    const val ITERATIONS = 2
    const val PARALLELISM = 1
    const val MIN_PIN_LENGTH = 6

    private val random = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val b64 = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    fun isValidPin(pin: String): Boolean = pin.length >= MIN_PIN_LENGTH

    fun encrypt(secret: VaultSecret, pin: String, memoryKb: Int = MEMORY_KB, iterations: Int = ITERATIONS): String {
        require(isValidPin(pin)) { "PIN too short" }
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val key = deriveKey(pin, salt, memoryKb, iterations, PARALLELISM)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        val ct = cipher.doFinal(json.encodeToString(VaultSecret.serializer(), secret).toByteArray(Charsets.UTF_8))
        key.fill(0)
        val envelope = VaultEnvelope(
            m = memoryKb, t = iterations, p = PARALLELISM,
            salt = b64.encodeToString(salt), nonce = b64.encodeToString(nonce), ct = b64.encodeToString(ct),
        )
        return b64.encodeToString(json.encodeToString(VaultEnvelope.serializer(), envelope).toByteArray(Charsets.UTF_8))
    }

    /** @throws WrongPinException if the PIN does not fit, [VaultFormatException] for broken blobs. */
    fun decrypt(blob: String, pin: String): VaultSecret {
        val envelope = try {
            json.decodeFromString(VaultEnvelope.serializer(), String(b64d.decode(blob), Charsets.UTF_8))
        } catch (e: Exception) {
            throw VaultFormatException("Unreadable vault entry: ${e.message}")
        }
        if (envelope.v != 1 || envelope.kdf != "argon2id") throw VaultFormatException("Unsupported vault format ${envelope.v}/${envelope.kdf}")
        val key = deriveKey(pin, b64d.decode(envelope.salt), envelope.m, envelope.t, envelope.p)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, b64d.decode(envelope.nonce)))
        val plain = try {
            cipher.doFinal(b64d.decode(envelope.ct))
        } catch (e: AEADBadTagException) {
            throw WrongPinException()
        } finally {
            key.fill(0)
        }
        return json.decodeFromString(VaultSecret.serializer(), String(plain, Charsets.UTF_8))
    }

    private fun deriveKey(pin: String, salt: ByteArray, memoryKb: Int, iterations: Int, parallelism: Int): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(memoryKb)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator().apply { init(params) }
        return ByteArray(32).also { generator.generateBytes(pin.toCharArray(), it) }
    }
}
