package de.erikedits.pocketbot.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class StoredBot(
    /** Discord application id of the bot. */
    val id: String,
    val name: String,
    val avatarUrl: String,
    val multiServer: Boolean = false,
    val categories: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    /** Was running when the app/phone last stopped -> restart after reboot. */
    val shouldRun: Boolean = false,
)

/** Bot list in files/bots.json; tokens separately in [SecureStore]. Phase 4 moves this to Room. */
class BotRepository(context: Context, private val secure: SecureStore) {
    private val file = File(context.filesDir, "bots.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(StoredBot.serializer())
    private val _bots = MutableStateFlow(load())
    val bots: StateFlow<List<StoredBot>> = _bots.asStateFlow()

    fun get(id: String): StoredBot? = _bots.value.firstOrNull { it.id == id }

    fun token(id: String): String? = secure.get(tokenKey(id))

    @Synchronized
    fun upsert(bot: StoredBot, token: String? = null) {
        if (token != null) secure.put(tokenKey(bot.id), token)
        save(_bots.value.filterNot { it.id == bot.id } + bot)
    }

    @Synchronized
    fun setShouldRun(id: String, run: Boolean) {
        val bot = get(id) ?: return
        if (bot.shouldRun != run) save(_bots.value.map { if (it.id == id) it.copy(shouldRun = run) else it })
    }

    @Synchronized
    fun delete(id: String) {
        secure.put(tokenKey(id), null)
        save(_bots.value.filterNot { it.id == id })
    }

    private fun load(): List<StoredBot> = try {
        if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(list: List<StoredBot>) {
        val sorted = list.sortedBy { it.createdAt }
        val tmp = File(file.parentFile, "bots.json.tmp")
        tmp.writeText(json.encodeToString(serializer, sorted))
        tmp.renameTo(file)
        _bots.value = sorted
    }

    private fun tokenKey(id: String) = "bot_token_$id"
}
