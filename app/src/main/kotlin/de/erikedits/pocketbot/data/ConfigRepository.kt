package de.erikedits.pocketbot.data

import android.content.Context
import de.erikedits.pocketbot.shared.config.BotConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/** The rules (functions) of each bot, as JSON in files/configs/<bot>.json. */
class ConfigRepository(context: Context) {
    private val dir = File(context.filesDir, "configs").apply { mkdirs() }
    private val _configs = MutableStateFlow<Map<String, BotConfig>>(emptyMap())
    val configs: StateFlow<Map<String, BotConfig>> = _configs.asStateFlow()

    @Synchronized
    fun get(botId: String): BotConfig {
        _configs.value[botId]?.let { return it }
        val file = File(dir, "$botId.json")
        val config = runCatching { BotConfig.fromJson(file.readText()) }.getOrNull() ?: BotConfig()
        _configs.update { it + (botId to config) }
        return config
    }

    @Synchronized
    fun save(botId: String, config: BotConfig) {
        val file = File(dir, "$botId.json")
        val tmp = File(dir, "$botId.json.tmp")
        tmp.writeText(config.toJson())
        tmp.renameTo(file)
        _configs.update { it + (botId to config) }
    }

    @Synchronized
    fun delete(botId: String) {
        File(dir, "$botId.json").delete()
        _configs.update { it - botId }
    }
}
