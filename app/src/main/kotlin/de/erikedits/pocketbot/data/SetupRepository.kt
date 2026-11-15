package de.erikedits.pocketbot.data

import android.content.Context
import de.erikedits.pocketbot.shared.catalog.BotSetup
import de.erikedits.pocketbot.shared.config.BotConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Bot types + answers per bot (files/setups/<bot>.json). Bots from the old rule builder
 * (files/configs/<bot>.json) are converted automatically: their rules keep running (SPEC-v2 6).
 */
class SetupRepository(context: Context) {
    private val dir = File(context.filesDir, "setups").apply { mkdirs() }
    private val legacyDir = File(context.filesDir, "configs")
    private val _setups = MutableStateFlow<Map<String, BotSetup>>(emptyMap())
    val setups: StateFlow<Map<String, BotSetup>> = _setups.asStateFlow()

    @Synchronized
    fun get(botId: String): BotSetup {
        _setups.value[botId]?.let { return it }
        val file = File(dir, "$botId.json")
        val setup = runCatching { BotSetup.fromJson(file.readText()) }.getOrNull()
            ?: migrate(botId)
            ?: BotSetup()
        _setups.update { it + (botId to setup) }
        return setup
    }

    private fun migrate(botId: String): BotSetup? {
        val old = File(legacyDir, "$botId.json").takeIf { it.exists() } ?: return null
        val config = runCatching { BotConfig.fromJson(old.readText()) }.getOrNull() ?: return null
        val setup = BotSetup.fromLegacy(config)
        writeFile(botId, setup)
        old.renameTo(File(legacyDir, "$botId.json.migrated"))
        return setup
    }

    @Synchronized
    fun save(botId: String, setup: BotSetup) {
        writeFile(botId, setup)
        _setups.update { it + (botId to setup) }
    }

    private fun writeFile(botId: String, setup: BotSetup) {
        val tmp = File(dir, "$botId.json.tmp")
        tmp.writeText(setup.toJson())
        tmp.renameTo(File(dir, "$botId.json"))
    }

    @Synchronized
    fun delete(botId: String) {
        File(dir, "$botId.json").delete()
        _setups.update { it - botId }
    }
}
