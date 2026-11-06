package de.erikedits.pocketbot.engine.rules

import de.erikedits.pocketbot.shared.config.VarScope
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Bot variables (coins, XP, counters …), SPEC 3.5. */
interface VariableStore {
    fun get(key: String): String?
    fun set(key: String, value: String)

    companion object {
        /** USER variables are per server and user, SERVER per server, GLOBAL once per bot. */
        fun key(scope: VarScope, name: String, guildId: String?, userId: String?): String = when (scope) {
            VarScope.USER -> "u:${guildId.orEmpty()}:${userId.orEmpty()}:$name"
            VarScope.SERVER -> "s:${guildId.orEmpty()}:$name"
            VarScope.GLOBAL -> "g:$name"
        }
    }
}

/** Keeps variables in memory and writes them to a JSON file after each change. */
class FileVariableStore(private val file: File?) : VariableStore {
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val values = ConcurrentHashMap<String, String>().apply {
        file?.takeIf { it.exists() }?.let { f -> runCatching { putAll(Json.decodeFromString(serializer, f.readText())) } }
    }
    private val lock = Any()

    override fun get(key: String): String? = values[key]

    override fun set(key: String, value: String) {
        values[key] = value
        val target = file ?: return
        synchronized(lock) {
            runCatching {
                target.parentFile?.mkdirs()
                val tmp = File(target.parentFile, target.name + ".tmp")
                tmp.writeText(Json.encodeToString(serializer, HashMap(values)))
                tmp.renameTo(target)
            }
        }
    }

    fun snapshot(): Map<String, String> = HashMap(values)
}
