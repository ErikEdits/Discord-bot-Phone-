package de.erikedits.pocketbot.engine.rules

import de.erikedits.pocketbot.shared.config.Action
import de.erikedits.pocketbot.shared.config.BotConfig
import de.erikedits.pocketbot.shared.config.CompareOp
import de.erikedits.pocketbot.shared.config.Condition
import de.erikedits.pocketbot.shared.config.MatchMode
import de.erikedits.pocketbot.shared.config.Rule
import de.erikedits.pocketbot.shared.config.TARGET_USER
import de.erikedits.pocketbot.shared.config.Trigger
import de.erikedits.pocketbot.shared.config.VarScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** Something that happened in Discord and may trigger rules. */
sealed interface TriggerEvent {
    data class Slash(val name: String) : TriggerEvent
    data class Message(val content: String) : TriggerEvent
    data object Join : TriggerEvent
    data object Leave : TriggerEvent
    data class Button(val buttonId: String) : TriggerEvent
    data class Interval(val ruleId: String) : TriggerEvent
}

/** Everything a rule can know about the event (filled by the JDA layer). */
data class Facts(
    val userId: String? = null,
    val userName: String = "",
    val displayName: String = "",
    val guildId: String? = null,
    val guildName: String = "",
    val memberCount: Int = 0,
    val channelId: String? = null,
    val channelName: String = "",
    val roleIds: Set<String> = emptySet(),
    val isAdmin: Boolean = false,
    /** Raw option values (ids for users/roles/channels). */
    val args: Map<String, String> = emptyMap(),
    /** How an option is shown in texts (e.g. a user mention). */
    val argDisplay: Map<String, String> = emptyMap(),
)

/** Side effects, implemented on top of JDA. Each call may throw; the runner logs and continues. */
interface ActionExecutor {
    fun reply(text: String, private: Boolean)
    fun buttonReply(text: String, label: String, buttonId: String)
    fun send(channelId: String, text: String)
    fun dm(userId: String, text: String)
    fun embed(channelId: String, title: String, description: String, color: Int)
    fun addRole(userId: String, roleId: String)
    fun removeRole(userId: String, roleId: String)
    fun timeout(userId: String, minutes: Long, reason: String)
    fun kick(userId: String, reason: String)
    fun ban(userId: String, reason: String)
    fun react(emoji: String)
    fun deleteMessage()
    fun notifyMe(text: String)
    fun log(message: String)
}

/**
 * The WENN → FALLS → DANN logic (SPEC 3), independent of Discord so it can be unit-tested.
 */
class RuleRunner(
    @Volatile var config: BotConfig,
    private val vars: VariableStore,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    private val cooldowns = ConcurrentHashMap<String, Long>()

    /** Rules whose trigger fits the event, in config order. Prefix commands also return the args text. */
    fun matching(event: TriggerEvent): List<Pair<Rule, Map<String, String>>> = config.rules.filter { it.enabled }.mapNotNull { rule ->
        when (val t = rule.trigger) {
            is Trigger.SlashCommand -> if (event is TriggerEvent.Slash && event.name == t.name) rule to emptyMap() else null
            is Trigger.PrefixCommand -> (event as? TriggerEvent.Message)?.let { prefixArgs(it.content, t.name) }?.let { rule to mapOf("text" to it) }
            is Trigger.Message -> if (event is TriggerEvent.Message && messageMatches(t, event.content)) rule to emptyMap() else null
            Trigger.MemberJoin -> if (event == TriggerEvent.Join) rule to emptyMap() else null
            Trigger.MemberLeave -> if (event == TriggerEvent.Leave) rule to emptyMap() else null
            is Trigger.ButtonClick -> if (event is TriggerEvent.Button && event.buttonId == t.buttonId) rule to emptyMap() else null
            is Trigger.Interval -> if (event is TriggerEvent.Interval && event.ruleId == rule.id) rule to emptyMap() else null
        }
    }

    private fun prefixArgs(content: String, name: String): String? {
        val head = config.prefix + name
        if (!content.startsWith(head, ignoreCase = true)) return null
        val rest = content.substring(head.length)
        if (rest.isNotEmpty() && !rest[0].isWhitespace()) return null
        return rest.trim()
    }

    private fun messageMatches(t: Trigger.Message, content: String): Boolean = when (t.mode) {
        MatchMode.ANY -> true
        MatchMode.CONTAINS -> t.text.isNotEmpty() && content.contains(t.text, t.ignoreCase)
        MatchMode.STARTS_WITH -> t.text.isNotEmpty() && content.startsWith(t.text, t.ignoreCase)
        MatchMode.EXACT -> content.trim().equals(t.text.trim(), t.ignoreCase)
        MatchMode.REGEX -> runCatching {
            Regex(t.text, if (t.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()).containsMatchIn(content)
        }.getOrDefault(false)
    }

    /** Result of running one rule. */
    enum class Outcome { RAN, ELSE, COOLDOWN }

    /**
     * Runs one rule: cooldown → conditions → actions (or else actions). Actions run in order;
     * variable actions apply immediately so later texts see the new values.
     */
    fun run(rule: Rule, facts: Facts, exec: ActionExecutor): Outcome {
        if (rule.cooldownSeconds > 0) {
            val key = rule.id + ":" + (facts.userId ?: facts.guildId ?: "")
            val now = clock()
            val until = cooldowns[key]
            if (until != null && now < until) {
                val remaining = ((until - now + 999) / 1000).toString()
                if (rule.trigger is Trigger.SlashCommand || rule.trigger is Trigger.ButtonClick) {
                    exec.reply(config.cooldownMessage.replace("{remaining}", remaining), private = true)
                }
                return Outcome.COOLDOWN
            }
            cooldowns[key] = now + rule.cooldownSeconds * 1000L
        }
        val ok = rule.conditions.all { check(it, facts) }
        val actions = if (ok) rule.actions else rule.elseActions
        actions.forEach { action ->
            try {
                execute(action, facts, exec)
            } catch (e: Exception) {
                exec.log("Rule \"${rule.name}\": ${action.javaClass.simpleName} failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        return if (ok) Outcome.RAN else Outcome.ELSE
    }

    fun check(condition: Condition, facts: Facts): Boolean = when (condition) {
        is Condition.HasRole -> condition.roleId.isNotBlank() && condition.roleId in facts.roleIds
        is Condition.LacksRole -> condition.roleId.isBlank() || condition.roleId !in facts.roleIds
        Condition.IsAdmin -> facts.isAdmin
        Condition.NotAdmin -> !facts.isAdmin
        is Condition.InChannel -> condition.channelId.isBlank() || condition.channelId == facts.channelId
        is Condition.Chance -> random.nextInt(100) < condition.percent.coerceIn(0, 100)
        is Condition.Variable -> compare(getVar(condition.scope, condition.name, facts), condition.op, render(condition.value, facts))
    }

    private fun compare(left: String, op: CompareOp, right: String): Boolean {
        val l = left.toDoubleOrNull()
        val r = right.toDoubleOrNull()
        if (l != null && r != null) {
            return when (op) {
                CompareOp.EQ -> l == r
                CompareOp.NE -> l != r
                CompareOp.LT -> l < r
                CompareOp.LE -> l <= r
                CompareOp.GT -> l > r
                CompareOp.GE -> l >= r
            }
        }
        val c = left.compareTo(right, ignoreCase = true)
        return when (op) {
            CompareOp.EQ -> c == 0
            CompareOp.NE -> c != 0
            CompareOp.LT -> c < 0
            CompareOp.LE -> c <= 0
            CompareOp.GT -> c > 0
            CompareOp.GE -> c >= 0
        }
    }

    private fun execute(action: Action, facts: Facts, exec: ActionExecutor) {
        fun r(text: String) = render(text, facts)
        when (action) {
            is Action.Reply -> exec.reply(r(action.text), action.private)
            is Action.Send -> exec.send(action.channelId, r(action.text))
            is Action.Dm -> exec.dm(target(action.target, facts), r(action.text))
            is Action.Embed -> exec.embed(action.channelId, r(action.title), r(action.description), parseColor(action.color))
            is Action.ButtonReply -> exec.buttonReply(r(action.text), r(action.label).ifBlank { "OK" }, action.buttonId)
            is Action.AddRole -> exec.addRole(target(action.target, facts), action.roleId)
            is Action.RemoveRole -> exec.removeRole(target(action.target, facts), action.roleId)
            is Action.Timeout -> exec.timeout(target(action.target, facts), r(action.minutes).trim().toLongOrNull()?.coerceIn(1, 40_320) ?: 10, r(action.reason))
            is Action.Kick -> exec.kick(target(action.target, facts), r(action.reason))
            is Action.Ban -> exec.ban(target(action.target, facts), r(action.reason))
            is Action.React -> exec.react(action.emoji)
            Action.DeleteMessage -> exec.deleteMessage()
            is Action.VarAdd -> {
                val current = getVar(action.scope, action.name, facts).toLongOrNull() ?: 0
                val delta = r(action.amount).trim().toLongOrNull() ?: 0
                setVar(action.scope, action.name, facts, (current + delta).toString())
            }
            is Action.VarSet -> setVar(action.scope, action.name, facts, r(action.value))
            is Action.Wait -> sleep(action.seconds.coerceIn(0, 300) * 1000L)
            is Action.NotifyMe -> exec.notifyMe(r(action.text))
        }
    }

    private fun target(target: String, facts: Facts): String {
        val id = if (target == TARGET_USER || target.isBlank()) facts.userId else facts.args[target.removePrefix("arg:")]
        return id?.takeIf { it.isNotBlank() } ?: error("no user for target \"$target\"")
    }

    private fun varKey(scope: VarScope, name: String, facts: Facts) = VariableStore.key(scope, name.trim(), facts.guildId, facts.userId)
    private fun getVar(scope: VarScope, name: String, facts: Facts) = vars.get(varKey(scope, name, facts)) ?: "0"
    private fun setVar(scope: VarScope, name: String, facts: Facts, value: String) = vars.set(varKey(scope, name, facts), value)

    private val placeholder = Regex("\\{([a-zA-Z]+(?:\\.[^{}:]+)?|random:[^{}]+|choice:[^{}]+)\\}")

    /** Replaces {user}, {user.mention}, {arg.x}, {var.x}, {random:1-6}, {choice:a|b} … (SPEC 3.1). */
    fun render(text: String, facts: Facts): String = placeholder.replace(text) { m ->
        val key = m.groupValues[1]
        when {
            key == "user" -> facts.displayName.ifBlank { facts.userName }
            key == "user.name" -> facts.userName
            key == "user.mention" -> facts.userId?.let { "<@$it>" } ?: facts.displayName
            key == "user.id" -> facts.userId.orEmpty()
            key == "server" -> facts.guildName
            key == "members" -> facts.memberCount.toString()
            key == "channel" -> facts.channelName
            key == "channel.mention" -> facts.channelId?.let { "<#$it>" }.orEmpty()
            key == "date" -> SimpleDateFormat("dd.MM.yyyy").format(Date(clock()))
            key == "time" -> SimpleDateFormat("HH:mm").format(Date(clock()))
            key.startsWith("arg.") -> key.removePrefix("arg.").let { facts.argDisplay[it] ?: facts.args[it] ?: "" }
            key.startsWith("var.") -> getVar(VarScope.USER, key.removePrefix("var."), facts)
            key.startsWith("servervar.") -> getVar(VarScope.SERVER, key.removePrefix("servervar."), facts)
            key.startsWith("globalvar.") -> getVar(VarScope.GLOBAL, key.removePrefix("globalvar."), facts)
            key.startsWith("random:") -> {
                val parts = key.removePrefix("random:").split('-').mapNotNull { it.trim().toLongOrNull() }
                if (parts.size == 2 && parts[0] <= parts[1]) random.nextLong(parts[0], parts[1] + 1).toString() else m.value
            }
            key.startsWith("choice:") -> key.removePrefix("choice:").split('|').filter { it.isNotEmpty() }.let { if (it.isEmpty()) "" else it[random.nextInt(it.size)] }
            else -> m.value
        }
    }

    private fun parseColor(hex: String): Int = hex.trim().removePrefix("#").toIntOrNull(16) ?: 0x5865F2
}
