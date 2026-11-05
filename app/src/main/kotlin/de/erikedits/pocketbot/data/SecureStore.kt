package de.erikedits.pocketbot.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted key/value store: AES-256-GCM with a non-exportable key in the Android Keystore.
 * Bot tokens and the session token live here and never leave the phone unencrypted (SPEC 6.1).
 */
class SecureStore(context: Context) {
    private val sp = context.getSharedPreferences("secure_store", Context.MODE_PRIVATE)

    /** Last read/write failure (for diagnostics on the dashboard), e.g. "AEADBadTagException: …". */
    @Volatile var lastError: String? = null
        private set

    fun contains(name: String): Boolean = sp.contains(name)

    @Synchronized
    fun put(name: String, value: String?) {
        if (value == null) {
            sp.edit { remove(name) }
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            check(iv.size == IV_SIZE) { "unexpected IV size ${iv.size}" }
            val encrypted = iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            // commit (not apply): the value must be on disk before e.g. the bot service reads it
            sp.edit(commit = true) { putString(name, Base64.encodeToString(encrypted, Base64.NO_WRAP)) }
        } catch (e: Exception) {
            lastError = "write $name: ${e.javaClass.simpleName}: ${e.message}"
            throw e
        }
    }

    @Synchronized
    fun get(name: String): String? {
        val stored = sp.getString(name, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
            String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
        } catch (e: Exception) {
            // Keep the stored value (a later attempt may work); report why it could not be read.
            lastError = "read $name: ${e.javaClass.simpleName}: ${e.message}"
            null
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "pocketbot_master_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
