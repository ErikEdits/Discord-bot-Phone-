package de.erikedits.pocketbot.engine.rules

import de.erikedits.pocketbot.shared.config.Action
import de.erikedits.pocketbot.shared.config.BotConfig
import de.erikedits.pocketbot.shared.config.CompareOp
import de.erikedits.pocketbot.shared.config.Condition
import de.erikedits.pocketbot.shared.config.MatchMode
import de.erikedits.pocketbot.shared.config.Rule
import de.erikedits.pocketbot.shared.config.Templates
import de.erikedits.pocketbot.shared.config.Trigger
import de.erikedits.pocketbot.shared.config.VarScope
import de.erikedits.pocketbot.shared.config.requiredIntents
import de.erikedits.pocketbot.shared.config.slashCommands
import de.erikedits.pocketbot.shared.discord.PrivilegedIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FakeExecutor : ActionExecutor {
    val calls = mutableListOf<String>()
    override fun reply(text: String, private: Boolean) { calls += "reply($text,$private)" }
    override fun buttonReply(text: String, label: String, buttonId: String) { calls += "button($text,$label,$buttonId)" }
    override fun send(channelId: String, text: String) { calls += "send($channelId,$text)" }
    override fun dm(userId: String, text: String) { calls += "dm($userId,$text)" }
    override fun embed(channelId: String, title: String, description: String, color: Int) { calls += "embed($title,$description,$color)" }
    override fun addRole(userId: String, roleId: String) { calls += "addRole($userId,$roleId)" }
    override fun removeRole(userId: String, roleId: String) { calls += "removeRole($userId,$roleId)" }
    override fun timeout(userId: String, minutes: Long, reason: String) { calls += "timeout($userId,$minutes,$reason)" }
    override fun kick(userId: String, reason: String) { calls += "kick($userId,$reason)" }
    override fun ban(userId: String, reason: String) { calls += "ban($userId,$reason)" }
    override fun react(emoji: String) { calls += "react($emoji)" }
    override fun deleteMessage() { calls += "delete" }
    override fun notifyMe(text: String) { calls += "notify($text)" }
    override fun log(message: String) { calls += "log($message)" }
}

class RuleRunnerTest {
    private val vars = FileVariableStore(null)
    private var now = 1_000_000L
    private fun runner(vararg rules: Rule, prefix: String = "!") =
        RuleRunner(BotConfig(prefix = prefix, rules = rules.toList()), vars, Random(42), clock = { now }, sleep = {})

    private val facts = Facts(
        userId = "1", userName = "erik", displayName = "Erik", guildId = "g", guildName = "Testserver",
        memberCount = 42, channelId = "c", channelName = "general", roleIds = setOf("r1"),
    )

    @Test
    fun `slash command with placeholders`() {
        val rule = Rule(name = "hi", trigger = Trigger.SlashCommand("hallo"), actions = listOf(Action.Reply("Hallo {user.mention} auf {server} ({members})")))
        val r = runner(rule)
        val (match, _) = r.matching(TriggerEvent.Slash("hallo")).single()
        val exec = FakeExecutor()
        r.run(match, facts, exec)
        assertEquals(listOf("reply(Hallo <@1> auf Testserver (42),false)"), exec.calls)
        assertTrue(r.matching(TriggerEvent.Slash("other")).isEmpty())
    }

    @Test
    fun `conditions pick then or else`() {
        val rule = Rule(
            name = "admin", trigger = Trigger.SlashCommand("x"), conditions = listOf(Condition.IsAdmin),
            actions = listOf(Action.Reply("yes")), elseActions = listOf(Action.Reply("no", private = true)),
        )
        val r = runner(rule)
        val exec = FakeExecutor()
        assertEquals(RuleRunner.Outcome.ELSE, r.run(rule, facts, exec))
        assertEquals(RuleRunner.Outcome.RAN, r.run(rule, facts.copy(isAdmin = true), exec))
        assertEquals(listOf("reply(no,true)", "reply(yes,false)"), exec.calls)
    }

    @Test
    fun `variables add, compare and render in order`() {
        val add = Rule(name = "daily", trigger = Trigger.SlashCommand("daily"), actions = listOf(Action.VarAdd(VarScope.USER, "coins", "100"), Action.Reply("Du hast {var.coins}")))
        val r = runner(add)
        val exec = FakeExecutor()
        r.run(add, facts, exec)
        r.run(add, facts, exec)
        r.run(add, facts.copy(userId = "2"), exec)
        assertEquals(listOf("reply(Du hast 100,false)", "reply(Du hast 200,false)", "reply(Du hast 100,false)"), exec.calls)
        assertTrue(r.check(Condition.Variable(VarScope.USER, "coins", CompareOp.GE, "200"), facts))
        assertTrue(!r.check(Condition.Variable(VarScope.USER, "coins", CompareOp.GT, "200"), facts))
    }

    @Test
    fun `cooldown blocks and tells the user`() {
        val rule = Rule(name = "c", trigger = Trigger.SlashCommand("c"), cooldownSeconds = 60, actions = listOf(Action.Reply("ok")))
        val r = runner(rule)
        val exec = FakeExecutor()
        assertEquals(RuleRunner.Outcome.RAN, r.run(rule, facts, exec))
        now += 10_000
        assertEquals(RuleRunner.Outcome.COOLDOWN, r.run(rule, facts, exec))
        now += 51_000
        assertEquals(RuleRunner.Outcome.RAN, r.run(rule, facts, exec))
        assertEquals(listOf("reply(ok,false)", "reply(⏳ Bitte warte noch 50 s.,true)", "reply(ok,false)"), exec.calls)
    }

    @Test
    fun `message triggers and prefix commands`() {
        val contains = Rule(name = "a", trigger = Trigger.Message("discord.gg/", MatchMode.CONTAINS), actions = listOf(Action.DeleteMessage))
        val prefix = Rule(name = "b", trigger = Trigger.PrefixCommand("sag"), actions = listOf(Action.Reply("{arg.text}")))
        val any = Rule(name = "c", trigger = Trigger.Message(mode = MatchMode.ANY), actions = listOf(Action.React("👍")))
        val r = runner(contains, prefix, any)
        assertEquals(listOf("a", "c"), r.matching(TriggerEvent.Message("komm auf DISCORD.GG/abc")).map { it.first.name })
        val m = r.matching(TriggerEvent.Message("!SAG hallo welt"))
        assertEquals(listOf("b", "c"), m.map { it.first.name })
        assertEquals("hallo welt", m.first().second["text"])
        assertTrue(r.matching(TriggerEvent.Message("!sagen x")).none { it.first.name == "b" })
    }

    @Test
    fun `member actions use the option target`() {
        val rule = Rule(name = "kick", trigger = Trigger.SlashCommand("kick"), actions = listOf(Action.Kick("{arg.grund}", "arg:user"), Action.Timeout("{arg.min}", "", "arg:user")))
        val exec = FakeExecutor()
        runner(rule).run(rule, facts.copy(args = mapOf("user" to "99", "grund" to "spam", "min" to "15")), exec)
        assertEquals(listOf("kick(99,spam)", "timeout(99,15,)"), exec.calls)
    }

    @Test
    fun `a failing action is logged and the rest still runs`() {
        val rule = Rule(name = "x", trigger = Trigger.SlashCommand("x"), actions = listOf(Action.Kick("", "arg:missing"), Action.Reply("weiter")))
        val exec = FakeExecutor()
        runner(rule).run(rule, facts, exec)
        assertTrue(exec.calls[0].startsWith("log(Rule \"x\": Kick failed"))
        assertEquals("reply(weiter,false)", exec.calls[1])
    }

    @Test
    fun `random and choice placeholders stay in range`() {
        val r = runner()
        repeat(50) {
            val n = r.render("{random:1-6}", facts).toInt()
            assertTrue(n in 1..6)
            assertTrue(r.render("{choice:a|b}", facts) in setOf("a", "b"))
        }
        assertEquals("{unknown} ok", r.render("{unknown} ok", facts))
    }

    @Test
    fun `all templates serialize, load and compute intents`() {
        Templates.categories.forEach { key ->
            listOf("de", "en").forEach { lang ->
                val cfg = Templates.merge(BotConfig(), Templates.forCategory(key, lang))
                val back = BotConfig.fromJson(cfg.toJson())
                assertEquals(cfg, back)
                assertTrue("$key has rules", back.rules.isNotEmpty())
                back.slashCommands().forEach { assertTrue(it.name, it.name.matches(Regex("^[a-z0-9_-]{1,32}$"))) }
            }
        }
        val community = Templates.forCategory("community", "de")
        assertEquals(setOf(PrivilegedIntent.SERVER_MEMBERS), BotConfig(rules = community.rules).requiredIntents())
        assertEquals(setOf(PrivilegedIntent.MESSAGE_CONTENT), BotConfig(rules = Templates.forCategory("moderation", "de").rules).requiredIntents())
    }

    @Test
    fun `merge skips duplicate slash commands`() {
        val t = Templates.forCategory("gaming", "de")
        val once = Templates.merge(BotConfig(), t)
        val twice = Templates.merge(once, t)
        assertEquals(once.rules.size, twice.rules.size)
    }

    @Test
    fun `variables persist in the file`() {
        val file = kotlin.io.path.createTempFile("vars", ".json").toFile().also { it.delete() }
        FileVariableStore(file).set("g:x", "5")
        assertEquals("5", FileVariableStore(file).get("g:x"))
        file.delete()
    }
}
