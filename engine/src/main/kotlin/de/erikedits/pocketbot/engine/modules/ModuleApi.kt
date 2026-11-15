package de.erikedits.pocketbot.engine.modules

import de.erikedits.pocketbot.engine.LogLevel
import de.erikedits.pocketbot.engine.rules.FileVariableStore
import de.erikedits.pocketbot.shared.discord.PrivilegedIntent
import kotlinx.serialization.json.JsonObject
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import okhttp3.OkHttpClient
import java.io.File
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Draws images on the phone (Android Canvas); null where not available (tests, runner). */
interface ImageRenderer {
    fun welcomeCard(avatar: ByteArray?, title: String, name: String, subtitle: String, color: Int): ByteArray
    fun rankCard(avatar: ByteArray?, name: String, level: Int, xp: Long, needed: Long, rank: Int, color: Int): ByteArray
}

/** What a module may use from the engine. */
interface ModuleContext {
    fun jda(): JDA?
    fun log(level: LogLevel, message: String)
    val store: FileVariableStore
    /** Per-bot folder (e.g. images/ collection), null in tests. */
    val dataDir: File?
    val http: OkHttpClient
    val scheduler: ScheduledExecutorService
    val renderer: ImageRenderer?
    fun notify(text: String)
    /** Runs blocking Discord work off the event thread. */
    fun execute(block: () -> Unit)
}

/** Input from a control in the app. */
data class ControlInput(
    val text: String? = null,
    val guildId: String? = null,
    val channelId: String? = null,
    val image: ByteArray? = null,
    val imageName: String? = null,
)

enum class InputKind { NONE, TEXT, GUILD, CHANNEL, IMAGE_AND_CHANNEL }

data class PanelAction(val id: String, val label: String, val input: InputKind = InputKind.NONE, val confirm: String? = null)
data class PanelItem(val id: String, val title: String, val subtitle: String = "", val actions: List<PanelAction> = emptyList())

/** The control surface of one module in the app dashboard (SPEC-v2 3). */
data class ModulePanel(
    val typeId: String,
    val lines: List<String> = emptyList(),
    val items: List<PanelItem> = emptyList(),
    val actions: List<PanelAction> = emptyList(),
)

/** A built-in bot module (tickets, server setup …) configured by the answers of one bot type. */
abstract class BotModule(val typeId: String, protected val config: JsonObject) {
    lateinit var ctx: ModuleContext
    private val timers = mutableListOf<ScheduledFuture<*>>()

    open fun requiredIntents(): Set<PrivilegedIntent> = emptySet()

    /** Slash commands of this module for one server (names may depend on the server language). */
    open fun commands(guild: Guild): List<CommandData> = emptyList()

    open fun onReady(api: JDA) = Unit
    open fun onGuildJoin(guild: Guild) = Unit
    /** true = handled (no other module/rule sees it). */
    open fun onSlash(e: SlashCommandInteractionEvent): Boolean = false
    open fun onButton(e: ButtonInteractionEvent): Boolean = false
    open fun onMessage(e: MessageReceivedEvent) = Unit
    open fun onMemberJoin(e: GuildMemberJoinEvent) = Unit
    open fun onMemberLeave(e: GuildMemberRemoveEvent) = Unit

    open fun panel(lang: String): ModulePanel = ModulePanel(typeId)
    /** Runs an app control; returns a short result text. Called off the main thread. */
    open fun perform(action: String, item: String?, input: ControlInput, lang: String): String = "?"

    open fun stop() {
        synchronized(timers) { timers.forEach { it.cancel(false) }; timers.clear() }
    }

    protected fun info(msg: String) = ctx.log(LogLevel.INFO, "[$typeId] $msg")
    protected fun warn(msg: String) = ctx.log(LogLevel.WARN, "[$typeId] $msg")

    protected fun keep(f: ScheduledFuture<*>) = synchronized(timers) { timers += f }

    /** Runs [task] every day at [time] (HH:mm, phone time zone). */
    protected fun daily(time: String, task: () -> Unit) {
        val t = runCatching { LocalTime.parse(time.padStart(5, '0')) }.getOrDefault(LocalTime.of(18, 0))
        fun scheduleNext() {
            val now = ZonedDateTime.now()
            var next = now.with(t).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = java.time.Duration.between(now, next).toMillis()
            keep(ctx.scheduler.schedule({
                runCatching(task).onFailure { warn("scheduled task failed: ${it.message}") }
                scheduleNext()
            }, delay, TimeUnit.MILLISECONDS))
        }
        scheduleNext()
    }
}
