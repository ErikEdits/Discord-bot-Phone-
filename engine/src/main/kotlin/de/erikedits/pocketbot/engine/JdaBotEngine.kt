package de.erikedits.pocketbot.engine

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent
import net.dv8tion.jda.api.events.session.SessionRecreateEvent
import net.dv8tion.jda.api.events.session.SessionResumeEvent
import net.dv8tion.jda.api.events.session.ShutdownEvent
import net.dv8tion.jda.api.exceptions.InvalidTokenException
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.CloseCode
import net.dv8tion.jda.api.requests.GatewayIntent
import java.util.EnumSet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Bot engine on top of JDA 5.
 *
 * Phase 2 (skeleton): connects, keeps status/ping/guild stats up to date and answers the
 * built-in `/ping` command so it can be tested on real phones. The rule engine (WENN → FALLS → DANN)
 * plugs in here in phase 4.
 */
class JdaBotEngine(
    private val token: String,
    private val intents: Set<GatewayIntent> = EnumSet.of(GatewayIntent.GUILD_MESSAGES),
    private val clock: () -> Long = System::currentTimeMillis,
) : BotEngine {

    private val _state = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val logBuffer = LogBuffer(clock = clock)
    override val logs: StateFlow<List<LogEntry>> = logBuffer.entries

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "pocketbot-engine").apply { isDaemon = true } }
    private val lock = Any()
    private var jda: JDA? = null
    private var pingTask: ScheduledFuture<*>? = null
    private var connectWatch: ScheduledFuture<*>? = null

    /** Set while we shut JDA down on purpose, so the ShutdownEvent is not reported as a crash. */
    @Volatile private var stopping = false

    override fun start() {
        synchronized(lock) {
            if (_state.value.status.isActive) return
            stopping = false
            _state.update { it.copy(status = BotStatus.CONNECTING, error = null) }
        }
        EngineLogSink.register(logBuffer)
        logBuffer.info("Connecting to Discord … (Android ${System.getProperty("java.vm.version") ?: "?"})")
        worker.execute { connect() }
        synchronized(lock) {
            connectWatch?.cancel(false)
            connectWatch = worker.schedule({
                if (_state.value.status == BotStatus.CONNECTING) {
                    logBuffer.warn("Still connecting after 60 s. Check the internet connection and the log lines above.")
                }
            }, 60, TimeUnit.SECONDS)
        }
    }

    private fun connect() {
        try {
            val instance = JDABuilder.createLight(token, intents)
                .setAutoReconnect(true)
                .addEventListeners(Listener())
                .build()
            synchronized(lock) {
                if (stopping) {
                    instance.shutdownNow()
                    return
                }
                jda = instance
            }
        } catch (e: InvalidTokenException) {
            logBuffer.error("The token is invalid. Was it reset in the Developer Portal?")
            _state.update { EngineState(status = BotStatus.INVALID_TOKEN, error = "invalid_token") }
            _events.tryEmit(EngineEvent.InvalidToken)
        } catch (e: Throwable) {
            // Throwable on purpose: an Error (e.g. NoClassDefFoundError) would otherwise be swallowed
            // by the executor and leave the bot stuck in CONNECTING without any log line.
            logBuffer.error("Start failed:\n${e.shortTrace()}")
            _state.update { EngineState(status = BotStatus.ERROR, error = e.message ?: e.javaClass.simpleName) }
            _events.tryEmit(EngineEvent.Crashed(e.message ?: e.javaClass.simpleName))
        }
    }

    override fun stop(pause: Boolean) {
        val instance: JDA?
        synchronized(lock) {
            stopping = true
            instance = jda
            jda = null
            pingTask?.cancel(false)
            pingTask = null
            connectWatch?.cancel(false)
            connectWatch = null
            _state.value = EngineState(status = if (pause) BotStatus.PAUSED else BotStatus.STOPPED)
        }
        EngineLogSink.unregister(logBuffer)
        logBuffer.info(if (pause) "Paused." else "Stopped.")
        if (instance != null) worker.execute {
            runCatching {
                instance.shutdown()
                if (!instance.awaitShutdown(5, TimeUnit.SECONDS)) instance.shutdownNow()
            }.onFailure { logBuffer.warn("Shutdown: ${it.message}") }
        }
    }

    private fun refreshStats(api: JDA) {
        val guilds = api.guildCache
        _state.update {
            it.copy(
                pingMs = api.gatewayPing,
                guilds = guilds.size().toInt(),
                members = guilds.sumOf { g -> g.memberCount },
                botName = api.selfUser.name,
            )
        }
    }

    private inner class Listener : ListenerAdapter() {
        override fun onReady(event: ReadyEvent) {
            val api = event.jda
            _state.update { it.copy(status = BotStatus.ONLINE, onlineSince = clock(), error = null) }
            refreshStats(api)
            logBuffer.info("Online as ${api.selfUser.name} on ${event.guildTotalCount} server(s).")
            api.updateCommands()
                .addCommands(Commands.slash("ping", "Check whether the bot is alive (PocketBot)"))
                .queue(
                    { logBuffer.info("Slash commands registered.") },
                    { logBuffer.warn("Registering slash commands failed: ${it.message}") },
                )
            synchronized(lock) {
                pingTask?.cancel(false)
                pingTask = worker.scheduleWithFixedDelay({ runCatching { refreshStats(api) } }, 30, 30, TimeUnit.SECONDS)
            }
        }

        override fun onSessionDisconnect(event: SessionDisconnectEvent) {
            if (stopping) return
            _state.update { it.copy(status = BotStatus.RECONNECTING) }
            logBuffer.warn("Connection lost (${event.closeCode?.meaning ?: "network"}), reconnecting …")
        }

        override fun onSessionResume(event: SessionResumeEvent) {
            _state.update { it.copy(status = BotStatus.ONLINE) }
            logBuffer.info("Reconnected.")
        }

        override fun onSessionRecreate(event: SessionRecreateEvent) {
            _state.update { it.copy(status = BotStatus.ONLINE) }
            refreshStats(event.jda)
            logBuffer.info("Reconnected (new session).")
        }

        override fun onGuildJoin(event: GuildJoinEvent) {
            refreshStats(event.jda)
            logBuffer.info("Added to server: ${event.guild.name}")
            _events.tryEmit(EngineEvent.JoinedGuild(event.guild.name, event.guild.memberCount))
        }

        override fun onGuildLeave(event: GuildLeaveEvent) {
            refreshStats(event.jda)
            logBuffer.info("Removed from server: ${event.guild.name}")
        }

        override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
            if (event.name == "ping") {
                event.reply("Pong! 🏓 ${event.jda.gatewayPing} ms · läuft auf PocketBot 📱").queue()
                logBuffer.info("/ping by ${event.user.name}")
            }
        }

        override fun onShutdown(event: ShutdownEvent) {
            if (stopping) return
            synchronized(lock) {
                jda = null
                pingTask?.cancel(false)
                pingTask = null
            }
            when (event.closeCode) {
                CloseCode.AUTHENTICATION_FAILED -> {
                    logBuffer.error("Discord rejected the token.")
                    _state.value = EngineState(status = BotStatus.INVALID_TOKEN, error = "invalid_token")
                    _events.tryEmit(EngineEvent.InvalidToken)
                }
                CloseCode.DISALLOWED_INTENTS -> {
                    logBuffer.error("A privileged intent is not enabled in the Developer Portal.")
                    _state.value = EngineState(status = BotStatus.DISALLOWED_INTENTS, error = "disallowed_intents")
                    _events.tryEmit(EngineEvent.Crashed("disallowed_intents"))
                }
                else -> {
                    val reason = event.closeCode?.meaning ?: "unknown"
                    logBuffer.error("Bot stopped unexpectedly: $reason")
                    _state.value = EngineState(status = BotStatus.ERROR, error = reason)
                    _events.tryEmit(EngineEvent.Crashed(reason))
                }
            }
        }
    }
}
