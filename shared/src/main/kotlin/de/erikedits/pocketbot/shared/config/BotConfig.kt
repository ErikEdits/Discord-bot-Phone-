package de.erikedits.pocketbot.shared.config

import de.erikedits.pocketbot.shared.discord.PrivilegedIntent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Everything a PocketBot does, built from WENN → FALLS → DANN rules (SPEC 3).
 * This is also the export / marketplace format (never contains the token).
 */
@Serializable
data class BotConfig(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    /** Prefix for prefix commands, e.g. "!". */
    val prefix: String = "!",
    val rules: List<Rule> = emptyList(),
    @SerialName("cooldown_message") val cooldownMessage: String = "⏳ Bitte warte noch {remaining} s.",
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "type"; prettyPrint = true }
        fun fromJson(text: String): BotConfig = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val trigger: Trigger,
    val conditions: List<Condition> = emptyList(),
    val actions: List<Action> = emptyList(),
    /** Run when a condition is not met (SONST). */
    @SerialName("else_actions") val elseActions: List<Action> = emptyList(),
    @SerialName("cooldown_seconds") val cooldownSeconds: Int = 0,
)

// ---------------------------------------------------------------- WENN

@Serializable
sealed interface Trigger {
    @Serializable @SerialName("slash")
    data class SlashCommand(val name: String, val description: String = "", val options: List<CommandOption> = emptyList()) : Trigger

    @Serializable @SerialName("prefix")
    data class PrefixCommand(val name: String) : Trigger

    @Serializable @SerialName("message")
    data class Message(val text: String = "", val mode: MatchMode = MatchMode.CONTAINS, @SerialName("ignore_case") val ignoreCase: Boolean = true) : Trigger

    @Serializable @SerialName("member_join")
    data object MemberJoin : Trigger

    @Serializable @SerialName("member_leave")
    data object MemberLeave : Trigger

    @Serializable @SerialName("button")
    data class ButtonClick(@SerialName("button_id") val buttonId: String) : Trigger

    @Serializable @SerialName("interval")
    data class Interval(val minutes: Int = 60, @SerialName("channel_id") val channelId: String = "") : Trigger
}

@Serializable
enum class MatchMode { CONTAINS, STARTS_WITH, EXACT, REGEX, ANY }

@Serializable
data class CommandOption(
    val name: String,
    val description: String = "",
    val type: OptionType = OptionType.TEXT,
    val required: Boolean = true,
)

@Serializable
enum class OptionType { TEXT, NUMBER, USER, ROLE, CHANNEL, YES_NO }

// ---------------------------------------------------------------- FALLS

@Serializable
sealed interface Condition {
    @Serializable @SerialName("has_role")
    data class HasRole(@SerialName("role_id") val roleId: String = "") : Condition

    @Serializable @SerialName("lacks_role")
    data class LacksRole(@SerialName("role_id") val roleId: String = "") : Condition

    @Serializable @SerialName("is_admin")
    data object IsAdmin : Condition

    @Serializable @SerialName("not_admin")
    data object NotAdmin : Condition

    @Serializable @SerialName("in_channel")
    data class InChannel(@SerialName("channel_id") val channelId: String = "") : Condition

    @Serializable @SerialName("variable")
    data class Variable(val scope: VarScope = VarScope.USER, val name: String = "", val op: CompareOp = CompareOp.GE, val value: String = "0") : Condition

    @Serializable @SerialName("chance")
    data class Chance(val percent: Int = 50) : Condition
}

@Serializable
enum class VarScope { USER, SERVER, GLOBAL }

@Serializable
enum class CompareOp { EQ, NE, LT, LE, GT, GE }

// ---------------------------------------------------------------- DANN

/** Who a member action applies to: "user" (who triggered it) or "arg:<option name>". */
const val TARGET_USER = "user"

@Serializable
sealed interface Action {
    @Serializable @SerialName("reply")
    data class Reply(val text: String = "", val private: Boolean = false) : Action

    @Serializable @SerialName("send")
    data class Send(@SerialName("channel_id") val channelId: String = "", val text: String = "") : Action

    @Serializable @SerialName("dm")
    data class Dm(val text: String = "", val target: String = TARGET_USER) : Action

    @Serializable @SerialName("embed")
    data class Embed(val title: String = "", val description: String = "", val color: String = "#5865F2", @SerialName("channel_id") val channelId: String = "") : Action

    @Serializable @SerialName("button_reply")
    data class ButtonReply(val text: String = "", val label: String = "OK", @SerialName("button_id") val buttonId: String = "button") : Action

    @Serializable @SerialName("add_role")
    data class AddRole(@SerialName("role_id") val roleId: String = "", val target: String = TARGET_USER) : Action

    @Serializable @SerialName("remove_role")
    data class RemoveRole(@SerialName("role_id") val roleId: String = "", val target: String = TARGET_USER) : Action

    @Serializable @SerialName("timeout")
    data class Timeout(val minutes: String = "10", val reason: String = "", val target: String = TARGET_USER) : Action

    @Serializable @SerialName("kick")
    data class Kick(val reason: String = "", val target: String = TARGET_USER) : Action

    @Serializable @SerialName("ban")
    data class Ban(val reason: String = "", val target: String = TARGET_USER) : Action

    @Serializable @SerialName("react")
    data class React(val emoji: String = "👍") : Action

    @Serializable @SerialName("delete_message")
    data object DeleteMessage : Action

    @Serializable @SerialName("var_add")
    data class VarAdd(val scope: VarScope = VarScope.USER, val name: String = "", val amount: String = "1") : Action

    @Serializable @SerialName("var_set")
    data class VarSet(val scope: VarScope = VarScope.USER, val name: String = "", val value: String = "0") : Action

    @Serializable @SerialName("wait")
    data class Wait(val seconds: Int = 5) : Action

    @Serializable @SerialName("notify_me")
    data class NotifyMe(val text: String = "") : Action
}

/** Discord rule for command and option names. */
private val commandNameRegex = Regex("^[-_\\p{L}\\p{N}]{1,32}$")

fun sanitizeCommandName(raw: String): String =
    raw.trim().lowercase().replace(' ', '-').filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(32)

fun isValidCommandName(name: String): Boolean = commandNameRegex.matches(name) && name == name.lowercase()

/** Privileged intents this configuration needs (switch on in the Developer Portal). */
fun BotConfig.requiredIntents(): Set<PrivilegedIntent> {
    val active = rules.filter { it.enabled }
    val out = mutableSetOf<PrivilegedIntent>()
    if (active.any { it.trigger is Trigger.PrefixCommand || (it.trigger is Trigger.Message && it.trigger.mode != MatchMode.ANY) }) {
        out += PrivilegedIntent.MESSAGE_CONTENT
    }
    if (active.any { it.trigger is Trigger.MemberJoin || it.trigger is Trigger.MemberLeave }) {
        out += PrivilegedIntent.SERVER_MEMBERS
    }
    return out
}

/** Slash commands to register: one per name (the first rule's options win). */
fun BotConfig.slashCommands(): List<Trigger.SlashCommand> =
    rules.filter { it.enabled }.mapNotNull { it.trigger as? Trigger.SlashCommand }
        .filter { isValidCommandName(it.name) }
        .distinctBy { it.name }
