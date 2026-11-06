package de.erikedits.pocketbot.engine

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class BotStatus { STOPPED, CONNECTING, ONLINE, RECONNECTING, PAUSED, INVALID_TOKEN, DISALLOWED_INTENTS, ERROR }

val BotStatus.isActive: Boolean
    get() = this == BotStatus.CONNECTING || this == BotStatus.ONLINE || this == BotStatus.RECONNECTING

data class EngineState(
    val status: BotStatus = BotStatus.STOPPED,
    /** Epoch millis of the last successful start, null while stopped. */
    val onlineSince: Long? = null,
    val pingMs: Long = -1,
    val guilds: Int = 0,
    val members: Int = 0,
    val botName: String? = null,
    val error: String? = null,
)

enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(val time: Long, val level: LogLevel, val message: String)

/** One-off things the app turns into push notifications. */
sealed interface EngineEvent {
    /** Action "Push an mich" from a rule. */
    data class Notify(val text: String) : EngineEvent
    data class JoinedGuild(val guildName: String, val memberCount: Int) : EngineEvent
    data object InvalidToken : EngineEvent
    data class Crashed(val reason: String) : EngineEvent
}

/**
 * A running Discord bot. Implementations must be safe to call from any thread.
 * The JDA implementation can later be swapped for a slimmer gateway client (SPEC 1.2).
 */
interface BotEngine {
    val state: StateFlow<EngineState>
    val logs: StateFlow<List<LogEntry>>
    val events: SharedFlow<EngineEvent>

    /** Connects to Discord. No-op while already active. */
    fun start()

    /** Disconnects; [pause] keeps status PAUSED (battery protection) instead of STOPPED. */
    fun stop(pause: Boolean = false)

    /** Applies edited rules while running (re-registers commands, reconnects if intents changed). */
    fun updateConfig(config: de.erikedits.pocketbot.shared.config.BotConfig)

    /** Servers with channels and roles, for the pickers in the editor (empty while offline). */
    fun snapshot(): List<GuildInfo>
}

data class NamedId(val id: String, val name: String)

data class GuildInfo(val id: String, val name: String, val channels: List<NamedId>, val roles: List<NamedId>)
