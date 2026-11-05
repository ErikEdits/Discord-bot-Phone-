package de.erikedits.pocketbot.data

import de.erikedits.pocketbot.shared.api.PocketBotApi
import de.erikedits.pocketbot.shared.api.VaultPut
import de.erikedits.pocketbot.shared.vault.VaultCrypto
import de.erikedits.pocketbot.shared.vault.VaultSecret
import de.erikedits.pocketbot.shared.vault.WrongPinException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cloud vault (SPEC 6.2): bot tokens end-to-end encrypted with the user's PIN before upload.
 * The PIN is kept in the Keystore-backed [SecureStore] on this phone so it is asked only once.
 */
class VaultRepository(
    private val api: PocketBotApi,
    private val secure: SecureStore,
    private val bots: BotRepository,
) {
    fun hasPin(): Boolean = secure.get(KEY_PIN) != null

    fun setPin(pin: String) {
        require(VaultCrypto.isValidPin(pin))
        secure.put(KEY_PIN, pin)
    }

    /** Encrypts and uploads one bot. Returns true on success and records the state on the bot. */
    suspend fun upload(botId: String): Boolean {
        val bot = bots.get(botId) ?: return false
        val token = bots.token(botId) ?: return false
        val pin = secure.get(KEY_PIN) ?: return false
        return try {
            val blob = withContext(Dispatchers.Default) {
                VaultCrypto.encrypt(VaultSecret(token = token, name = bot.name, avatarUrl = bot.avatarUrl), pin)
            }
            api.putVault(botId, VaultPut(label = bot.name.take(64), blob = blob))
            bots.setCloud(botId, StoredBot.CLOUD_SYNCED)
            true
        } catch (e: Exception) {
            bots.setCloud(botId, StoredBot.CLOUD_FAILED)
            false
        }
    }

    suspend fun remove(botId: String) {
        api.deleteVault(botId)
        bots.setCloud(botId, StoredBot.CLOUD_NONE)
    }

    /** Number of bots stored in the cloud for this account. */
    suspend fun count(): Int = api.vault().items.size

    /**
     * Downloads and decrypts all vault entries with [pin] and saves them on this phone.
     * @throws WrongPinException when the PIN does not fit.
     */
    suspend fun restore(pin: String): Int {
        val items = api.vault().items
        val secrets = withContext(Dispatchers.Default) { items.map { it to VaultCrypto.decrypt(it.blob, pin) } }
        secure.put(KEY_PIN, pin)
        secrets.forEach { (item, secret) ->
            val existing = bots.get(item.botId)
            bots.upsert(
                (existing ?: StoredBot(id = item.botId, name = secret.name, avatarUrl = secret.avatarUrl.orEmpty()))
                    .copy(cloud = StoredBot.CLOUD_SYNCED),
                token = secret.token,
            )
        }
        return secrets.size
    }

    private companion object {
        const val KEY_PIN = "vault_pin"
    }
}
