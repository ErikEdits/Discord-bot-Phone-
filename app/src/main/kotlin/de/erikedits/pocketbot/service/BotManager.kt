package de.erikedits.pocketbot.service

import de.erikedits.pocketbot.data.BotRepository
import de.erikedits.pocketbot.engine.BotEngine
import de.erikedits.pocketbot.engine.BotStatus
import de.erikedits.pocketbot.engine.EngineEvent
import de.erikedits.pocketbot.engine.EngineState
import de.erikedits.pocketbot.engine.JdaBotEngine
import de.erikedits.pocketbot.engine.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class BotEvent(val botId: String, val event: EngineEvent)

/** Owns one engine per bot. The foreground service keeps the process alive while any is active. */
class BotManager(
    private val repo: BotRepository,
    private val scope: CoroutineScope,
    private val engineFactory: (token: String) -> BotEngine = { JdaBotEngine(it) },
) {
    private class Entry(val engine: BotEngine, val token: String, val jobs: List<Job>)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val _states = MutableStateFlow<Map<String, EngineState>>(emptyMap())
    val states: StateFlow<Map<String, EngineState>> = _states.asStateFlow()

    private val _events = MutableSharedFlow<BotEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<BotEvent> = _events.asSharedFlow()

    fun engine(id: String): BotEngine? = entries[id]?.engine

    /** Starts the bot; false when no token is stored. */
    fun start(id: String): Boolean {
        val token = repo.token(id) ?: return false
        val entry = entries[id]?.takeIf { it.token == token } ?: run {
            remove(id)
            create(id, token)
        }
        repo.setShouldRun(id, true)
        entry.engine.start()
        return true
    }

    fun stop(id: String) {
        repo.setShouldRun(id, false)
        entries[id]?.engine?.stop()
    }

    fun stopAll() = entries.keys.toList().forEach(::stop)

    /** Battery protection: pause running bots but remember they should run. */
    fun pauseAll() = entries.values.filter { it.engine.state.value.status.isActive }.forEach { it.engine.stop(pause = true) }

    fun resumePaused() = entries.values.filter { it.engine.state.value.status == BotStatus.PAUSED }.forEach { it.engine.start() }

    /** Bot deleted or token replaced. */
    fun remove(id: String) {
        val entry = entries.remove(id) ?: return
        entry.engine.stop()
        entry.jobs.forEach(Job::cancel)
        _states.update { it - id }
    }

    /** True while any bot is connecting/online/reconnecting or paused by battery protection. */
    fun hasWork(): Boolean = entries.values.any { it.engine.state.value.status.let { s -> s.isActive || s == BotStatus.PAUSED } }

    fun activeCount(): Int = entries.values.count { it.engine.state.value.status.isActive }

    private fun create(id: String, token: String): Entry {
        val engine = engineFactory(token)
        val jobs = listOf(
            scope.launch { engine.state.collect { s -> _states.update { it + (id to s) } } },
            scope.launch {
                engine.events.collect { event ->
                    if (event is EngineEvent.InvalidToken) repo.setShouldRun(id, false)
                    _events.emit(BotEvent(id, event))
                }
            },
        )
        return Entry(engine, token, jobs).also { entries[id] = it }
    }
}
