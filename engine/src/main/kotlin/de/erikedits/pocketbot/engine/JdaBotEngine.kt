package de.erikedits.pocketbot.engine

import de.erikedits.pocketbot.engine.rules.ActionExecutor
import de.erikedits.pocketbot.engine.rules.Facts
import de.erikedits.pocketbot.engine.rules.FileVariableStore
import de.erikedits.pocketbot.engine.rules.RuleRunner
import de.erikedits.pocketbot.engine.rules.TriggerEvent
import de.erikedits.pocketbot.shared.config.Action
import de.erikedits.pocketbot.shared.config.BotConfig
import de.erikedits.pocketbot.shared.config.OptionType
import de.erikedits.pocketbot.shared.config.Rule
import de.erikedits.pocketbot.shared.config.Trigger
import de.erikedits.pocketbot.shared.config.requiredIntents
import de.erikedits.pocketbot.shared.config.slashCommands
import de.erikedits.pocketbot.shared.discord.PrivilegedIntent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent
import net.dv8tion.jda.api.events.session.SessionRecreateEvent
import net.dv8tion.jda.api.events.session.SessionResumeEvent
import net.dv8tion.jda.api.events.session.ShutdownEvent
import net.dv8tion.jda.api.exceptions.InvalidTokenException
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.requests.CloseCode
import net.dv8tion.jda.api.requests.GatewayIntent
import java.io.File
import java.time.Duration
import java.util.EnumSet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import net.dv8tion.jda.api.interactions.commands.OptionType as JdaOptionType

/**
 * Bot engine on top of JDA 5: connection, status, logs, and the rule engine (WENN → FALLS → DANN).
 */
class JdaBotEngine(
    private val token: String,
    config: BotConfig = BotConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
    /** Optional file that keeps the log across app crashes. */
    logFile: File? = null,
    /** Optional file for the bot's variables (coins, XP …). */
    varsFile: File? = null,
) : BotEngine {

    private val _state = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val logBuffer = LogBuffer(clock = clock, file = logFile)
    override val logs: StateFlow<List<LogEntry>> = logBuffer.entries

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    private val runner = RuleRunner(config, FileVariableStore(varsFile), clock = clock)

    private val worker = Executors.newScheduledThreadPool(1) { r -> Thread(r, "pocketbot-engine").apply { isDaemon = true } }
    private val ruleThreads = Executors.newCachedThreadPool { r -> Thread(r, "pocketbot-rules").apply { isDaemon = true } }
    private val lock = Any()
    private var jda: JDA? = null
    private var pingTask: ScheduledFuture<*>? = null
    private var connectWatch: ScheduledFuture<*>? = null
    private val intervalTasks = mutableListOf<ScheduledFuture<*>>()
    private var connectedIntents: Set<GatewayIntent> = emptySet()

    /** Set while we shut JDA down on purpose, so the ShutdownEvent is not reported as a crash. */
    @Volatile private var stopping = false

    private fun intentsFor(config: BotConfig): Set<GatewayIntent> {
        val set = EnumSet.of(GatewayIntent.GUILD_MESSAGES)
        val needed = config.requiredIntents()
        if (PrivilegedIntent.MESSAGE_CONTENT in needed) set += GatewayIntent.MESSAGE_CONTENT
        if (PrivilegedIntent.SERVER_MEMBERS in needed) set += GatewayIntent.GUILD_MEMBERS
        return set
    }

    override fun start() {
        synchronized(lock) {
            if (_state.value.status.isActive) return
            stopping = false
            _state.update { it.copy(status = BotStatus.CONNECTING, error = null) }
        }
        EngineLogSink.register(logBuffer)
        logBuffer.info("Connecting to Discord … (${runner.config.rules.count { it.enabled }} active functions)")
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
            val intents = intentsFor(runner.config)
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
                connectedIntents = intents
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
            cancelIntervals()
            _state.value = EngineState(status = if (pause) BotStatus.PAUSED else BotStatus.STOPPED)
        }
        logBuffer.info(if (pause) "Paused." else "Stopped.")
        EngineLogSink.unregister(logBuffer)
        if (instance != null) worker.execute {
            runCatching {
                instance.shutdown()
                if (!instance.awaitShutdown(5, TimeUnit.SECONDS)) instance.shutdownNow()
            }.onFailure { logBuffer.warn("Shutdown: ${it.message}") }
        }
    }

    override fun updateConfig(config: BotConfig) {
        runner.config = config
        val api = jda ?: return
        if (intentsFor(config) != connectedIntents) {
            logBuffer.info("Functions changed and need other intents – reconnecting …")
            worker.execute {
                stop()
                // Give JDA a moment to shut down before connecting again.
                worker.schedule({ start() }, 3, TimeUnit.SECONDS)
            }
            return
        }
        logBuffer.info("Functions updated (${config.rules.count { it.enabled }} active).")
        api.guildCache.forEach(::registerCommands)
        scheduleIntervals(api)
    }

    override fun snapshot(): List<GuildInfo> {
        val api = jda ?: return emptyList()
        return api.guildCache.map { g ->
            GuildInfo(
                id = g.id,
                name = g.name,
                channels = (g.textChannels + g.newsChannels).map { NamedId(it.id, "#" + it.name) },
                roles = g.roles.filter { !it.isPublicRole && !it.isManaged }.map { NamedId(it.id, "@" + it.name) },
            )
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

    // ------------------------------------------------------------------ commands

    private fun buildCommands(): List<CommandData> {
        val custom = runner.config.slashCommands().map { cmd ->
            val data = Commands.slash(cmd.name, cmd.description.ifBlank { cmd.name }.take(100))
            cmd.options.filter { it.name.isNotBlank() }.sortedBy { !it.required }.forEach { o ->
                data.addOption(o.type.toJda(), o.name.lowercase().take(32), o.description.ifBlank { o.name }.take(100), o.required)
            }
            data
        }
        val ping = if (custom.none { it.name == "ping" }) {
            listOf(
                Commands.slash("ping", "Check whether the bot is alive (PocketBot)")
                    .setDescriptionLocalization(DiscordLocale.GERMAN, "Prüft, ob der Bot läuft (PocketBot)"),
            )
        } else {
            emptyList()
        }
        return ping + custom
    }

    private fun OptionType.toJda(): JdaOptionType = when (this) {
        OptionType.TEXT -> JdaOptionType.STRING
        OptionType.NUMBER -> JdaOptionType.INTEGER
        OptionType.USER -> JdaOptionType.USER
        OptionType.ROLE -> JdaOptionType.ROLE
        OptionType.CHANNEL -> JdaOptionType.CHANNEL
        OptionType.YES_NO -> JdaOptionType.BOOLEAN
    }

    private fun registerCommands(guild: Guild) {
        val commands = try {
            buildCommands()
        } catch (e: Exception) {
            logBuffer.error("A command is invalid: ${e.message}")
            return
        }
        guild.updateCommands().addCommands(commands).queue(
            { logBuffer.info("Slash commands ready on ${guild.name} (${commands.size}).") },
            { logBuffer.warn("Slash commands on ${guild.name} failed: ${it.message} (was the bot invited with 'applications.commands'?)") },
        )
    }

    // ------------------------------------------------------------------ intervals

    private fun cancelIntervals() {
        intervalTasks.forEach { it.cancel(false) }
        intervalTasks.clear()
    }

    private fun scheduleIntervals(api: JDA) = synchronized(lock) {
        cancelIntervals()
        runner.config.rules.filter { it.enabled && it.trigger is Trigger.Interval }.forEach { rule ->
            val t = rule.trigger as Trigger.Interval
            val minutes = t.minutes.coerceAtLeast(1).toLong()
            intervalTasks += worker.scheduleWithFixedDelay({
                runCatching {
                    val channel = api.getChannelById(GuildMessageChannel::class.java, t.channelId)
                    if (channel == null) {
                        logBuffer.warn("Interval \"${rule.name}\": channel not found")
                    } else {
                        val facts = Facts(guildId = channel.guild.id, guildName = channel.guild.name, memberCount = channel.guild.memberCount, channelId = channel.id, channelName = channel.name)
                        ruleThreads.execute { runRule(rule, facts, JdaExecutor(api, channel.guild, channel, null, null)) }
                    }
                }
            }, minutes, minutes, TimeUnit.MINUTES)
        }
    }

    // ------------------------------------------------------------------ rule execution

    private fun runRule(rule: Rule, facts: Facts, exec: JdaExecutor) {
        try {
            runner.run(rule, facts, exec)
        } catch (e: Throwable) {
            logBuffer.error("Rule \"${rule.name}\" crashed: ${e.shortTrace(3)}")
        }
    }

    private fun factsOf(user: User?, member: Member?, guild: Guild?, channel: GuildMessageChannel?) = Facts(
        userId = user?.id,
        userName = user?.name.orEmpty(),
        displayName = member?.effectiveName ?: user?.effectiveName.orEmpty(),
        guildId = guild?.id,
        guildName = guild?.name.orEmpty(),
        memberCount = guild?.memberCount ?: 0,
        channelId = channel?.id,
        channelName = channel?.name.orEmpty(),
        roleIds = member?.roles?.map { it.id }?.toSet() ?: emptySet(),
        isAdmin = member?.hasPermission(Permission.ADMINISTRATOR) == true || member?.hasPermission(Permission.MANAGE_SERVER) == true,
    )

    private fun optionArgs(options: List<OptionMapping>): Pair<Map<String, String>, Map<String, String>> {
        val raw = mutableMapOf<String, String>()
        val shown = mutableMapOf<String, String>()
        options.forEach { o ->
            when (o.type) {
                JdaOptionType.USER -> { raw[o.name] = o.asUser.id; shown[o.name] = o.asUser.asMention }
                JdaOptionType.ROLE -> { raw[o.name] = o.asRole.id; shown[o.name] = o.asRole.asMention }
                JdaOptionType.CHANNEL -> { raw[o.name] = o.asChannel.id; shown[o.name] = o.asChannel.asMention }
                JdaOptionType.BOOLEAN -> { raw[o.name] = o.asBoolean.toString(); shown[o.name] = if (o.asBoolean) "✅" else "❌" }
                else -> { raw[o.name] = o.asString; shown[o.name] = o.asString }
            }
        }
        return raw to shown
    }

    /** Whether the first reply of these rules should be private (decides how the interaction is acknowledged). */
    private fun firstReplyPrivate(rules: List<Rule>): Boolean {
        rules.forEach { rule ->
            rule.actions.forEach { a ->
                when (a) {
                    is Action.Reply -> return a.private
                    is Action.ButtonReply, is Action.Embed -> return false
                    else -> Unit
                }
            }
        }
        return true
    }

    /** Executes rule actions with JDA for one event. */
    private inner class JdaExecutor(
        private val api: JDA,
        private val guild: Guild?,
        private val channel: GuildMessageChannel?,
        private val message: Message?,
        private val hook: InteractionHook?,
    ) : ActionExecutor {
        @Volatile var replied = false

        private fun g(): Guild = guild ?: error("only works on a server")

        private fun targetChannel(channelId: String): GuildMessageChannel =
            (if (channelId.isBlank()) channel ?: guild?.systemChannel else api.getChannelById(GuildMessageChannel::class.java, channelId))
                ?: error("channel not found")

        override fun reply(text: String, private: Boolean) {
            if (text.isBlank()) return
            when {
                hook != null -> hook.sendMessage(text.take(2000)).setEphemeral(private).complete()
                message != null -> message.reply(text.take(2000)).complete()
                else -> targetChannel("").sendMessage(text.take(2000)).complete()
            }
            replied = true
        }

        override fun buttonReply(text: String, label: String, buttonId: String) {
            val button = Button.primary("pb:$buttonId", label.take(80))
            val content = text.ifBlank { "​" }.take(2000)
            when {
                hook != null -> hook.sendMessage(content).addActionRow(button).complete()
                message != null -> message.reply(content).addActionRow(button).complete()
                else -> targetChannel("").sendMessage(content).addActionRow(button).complete()
            }
            replied = true
        }

        override fun send(channelId: String, text: String) {
            if (text.isBlank()) return
            targetChannel(channelId).sendMessage(text.take(2000)).complete()
        }

        override fun dm(userId: String, text: String) {
            if (text.isBlank()) return
            api.openPrivateChannelById(userId).flatMap { it.sendMessage(text.take(2000)) }.complete()
        }

        override fun embed(channelId: String, title: String, description: String, color: Int) {
            val embed = EmbedBuilder()
                .setTitle(title.take(256).ifBlank { null })
                .setDescription(description.take(4096).ifBlank { null })
                .setColor(color)
                .build()
            when {
                channelId.isBlank() && hook != null -> { hook.sendMessageEmbeds(embed).complete(); replied = true }
                channelId.isBlank() && message != null -> message.replyEmbeds(embed).complete()
                else -> targetChannel(channelId).sendMessageEmbeds(embed).complete()
            }
        }

        override fun addRole(userId: String, roleId: String) {
            val role = g().getRoleById(roleId) ?: error("role not found")
            g().addRoleToMember(UserSnowflake.fromId(userId), role).complete()
        }

        override fun removeRole(userId: String, roleId: String) {
            val role = g().getRoleById(roleId) ?: error("role not found")
            g().removeRoleFromMember(UserSnowflake.fromId(userId), role).complete()
        }

        override fun timeout(userId: String, minutes: Long, reason: String) {
            g().timeoutFor(UserSnowflake.fromId(userId), Duration.ofMinutes(minutes)).reason(reason.ifBlank { null }).complete()
        }

        override fun kick(userId: String, reason: String) {
            g().kick(UserSnowflake.fromId(userId)).reason(reason.ifBlank { null }).complete()
        }

        override fun ban(userId: String, reason: String) {
            g().ban(UserSnowflake.fromId(userId), 0, TimeUnit.SECONDS).reason(reason.ifBlank { null }).complete()
        }

        override fun react(emoji: String) {
            (message ?: error("only works on messages")).addReaction(Emoji.fromFormatted(emoji)).complete()
        }

        override fun deleteMessage() {
            (message ?: error("only works on messages")).delete().complete()
        }

        override fun notifyMe(text: String) {
            _events.tryEmit(EngineEvent.Notify(text))
        }

        override fun log(message: String) = logBuffer.warn(message)
    }

    private inner class Listener : ListenerAdapter() {
        override fun onReady(event: ReadyEvent) {
            val api = event.jda
            _state.update { it.copy(status = BotStatus.ONLINE, onlineSince = clock(), error = null) }
            refreshStats(api)
            logBuffer.info("Online as ${api.selfUser.name} on ${event.guildTotalCount} server(s).")
            // Guild commands show up instantly (global ones can take a while and need a client reload).
            // Clear old global commands so nothing appears twice.
            api.updateCommands().queue(null) { logBuffer.warn("Clearing global commands failed: ${it.message}") }
            api.guildCache.forEach(::registerCommands)
            scheduleIntervals(api)
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
            registerCommands(event.guild)
            _events.tryEmit(EngineEvent.JoinedGuild(event.guild.name, event.guild.memberCount))
        }

        override fun onGuildLeave(event: GuildLeaveEvent) {
            refreshStats(event.jda)
            logBuffer.info("Removed from server: ${event.guild.name}")
        }

        override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
            val matches = runner.matching(TriggerEvent.Slash(event.name))
            if (matches.isEmpty()) {
                if (event.name == "ping") {
                    event.reply("Pong! 🏓 ${event.jda.gatewayPing} ms · läuft auf PocketBot 📱").queue()
                } else {
                    event.reply("This command is not active anymore.").setEphemeral(true).queue()
                }
                return
            }
            event.deferReply(firstReplyPrivate(matches.map { it.first })).queue()
            val channel = event.channel as? GuildMessageChannel
            val (raw, shown) = optionArgs(event.options)
            val facts = factsOf(event.user, event.member, event.guild, channel).copy(args = raw, argDisplay = shown)
            logBuffer.info("/${event.name} by ${event.user.name}")
            ruleThreads.execute {
                val exec = JdaExecutor(event.jda, event.guild, channel, null, event.hook)
                matches.forEach { (rule, _) -> runRule(rule, facts, exec) }
                if (!exec.replied) event.hook.editOriginal("✅").queue(null) { }
            }
        }

        override fun onButtonInteraction(event: ButtonInteractionEvent) {
            val id = event.componentId.removePrefix("pb:")
            if (!event.componentId.startsWith("pb:")) return
            val matches = runner.matching(TriggerEvent.Button(id))
            if (matches.isEmpty()) {
                event.deferEdit().queue()
                return
            }
            event.deferReply(firstReplyPrivate(matches.map { it.first })).queue()
            val channel = event.channel as? GuildMessageChannel
            val facts = factsOf(event.user, event.member, event.guild, channel)
            ruleThreads.execute {
                val exec = JdaExecutor(event.jda, event.guild, channel, null, event.hook)
                matches.forEach { (rule, _) -> runRule(rule, facts, exec) }
                if (!exec.replied) event.hook.deleteOriginal().queue(null) { }
            }
        }

        override fun onMessageReceived(event: MessageReceivedEvent) {
            if (event.author.isBot || !event.isFromGuild) return
            val matches = runner.matching(TriggerEvent.Message(event.message.contentRaw))
            if (matches.isEmpty()) return
            val channel = event.channel as? GuildMessageChannel
            val base = factsOf(event.author, event.member, event.guild, channel)
            ruleThreads.execute {
                val exec = JdaExecutor(event.jda, event.guild, channel, event.message, null)
                matches.forEach { (rule, args) -> runRule(rule, base.copy(args = args, argDisplay = args), exec) }
            }
        }

        override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
            val matches = runner.matching(TriggerEvent.Join)
            if (matches.isEmpty()) return
            val facts = factsOf(event.user, event.member, event.guild, null)
            ruleThreads.execute {
                val exec = JdaExecutor(event.jda, event.guild, null, null, null)
                matches.forEach { (rule, _) -> runRule(rule, facts, exec) }
            }
        }

        override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
            val matches = runner.matching(TriggerEvent.Leave)
            if (matches.isEmpty()) return
            val facts = factsOf(event.user, event.member, event.guild, null)
            ruleThreads.execute {
                val exec = JdaExecutor(event.jda, event.guild, null, null, null)
                matches.forEach { (rule, _) -> runRule(rule, facts, exec) }
            }
        }

        override fun onShutdown(event: ShutdownEvent) {
            if (stopping) return
            synchronized(lock) {
                jda = null
                pingTask?.cancel(false)
                pingTask = null
                cancelIntervals()
            }
            when (event.closeCode) {
                CloseCode.AUTHENTICATION_FAILED -> {
                    logBuffer.error("Discord rejected the token.")
                    _state.value = EngineState(status = BotStatus.INVALID_TOKEN, error = "invalid_token")
                    _events.tryEmit(EngineEvent.InvalidToken)
                }
                CloseCode.DISALLOWED_INTENTS -> {
                    logBuffer.error("A privileged intent is not enabled in the Developer Portal (Message Content / Server Members).")
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
