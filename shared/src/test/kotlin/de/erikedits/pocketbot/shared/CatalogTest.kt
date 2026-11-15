package de.erikedits.pocketbot.shared

import de.erikedits.pocketbot.shared.catalog.BotSetup
import de.erikedits.pocketbot.shared.catalog.Catalog
import de.erikedits.pocketbot.shared.catalog.QuestionType
import de.erikedits.pocketbot.shared.catalog.Questions
import de.erikedits.pocketbot.shared.catalog.RulesCompiler
import de.erikedits.pocketbot.shared.catalog.TypeSelection
import de.erikedits.pocketbot.shared.config.Action
import de.erikedits.pocketbot.shared.config.BotConfig
import de.erikedits.pocketbot.shared.config.Rule
import de.erikedits.pocketbot.shared.config.Trigger
import de.erikedits.pocketbot.shared.config.isValidCommandName
import de.erikedits.pocketbot.shared.config.slashCommands
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the real catalog in /catalog (the one the app downloads from GitHub). */
class CatalogTest {
    private val dir = File(System.getProperty("user.dir")).resolve("../catalog").canonicalFile
    private val catalog = Catalog.load(dir.resolve("index.json").readText()) { dir.resolve(it).takeIf(File::exists)?.readText() }

    private val modules = setOf("tickets", "server_setup", "images", "moderation", "welcome", "levels", "giveaway", "polls", "roles", "reminders", "rules")

    @Test
    fun `every catalog entry loads and is consistent`() {
        assertTrue(catalog.index.types.size >= 14)
        assertEquals(catalog.index.types.size, catalog.types.size)
        assertEquals(catalog.index.types.size, catalog.index.types.map { it.id }.toSet().size)
        catalog.types.values.forEach { type ->
            assertTrue("${type.id}: unknown module ${type.module}", type.module in modules)
            assertFalse("${type.id}: name", type.name.de.isBlank() || type.name.en.isBlank())
            val ids = type.questions.map { it.id }
            assertEquals("${type.id}: duplicate question ids", ids.size, ids.toSet().size)
            type.questions.forEach { q ->
                assertFalse("${type.id}.${q.id}: label", q.label.de.isBlank() || q.label.en.isBlank())
                q.showIf?.let { assertTrue("${type.id}.${q.id}: show_if → ${it.question}", it.question in ids) }
                if (q.type == QuestionType.CHOICE || q.type == QuestionType.MULTI_CHOICE) assertTrue("${type.id}.${q.id}: options", q.options.isNotEmpty())
                q.default?.let { d -> assertNotNull("${type.id}.${q.id}: default $d invalid", Questions.sanitize(q, d)) }
            }
        }
    }

    @Test
    fun `rule types compile with defaults into valid commands`() {
        catalog.types.values.filter { it.module == "rules" }.forEach { type ->
            val rules = RulesCompiler.compile(type, emptyMap())
            assertTrue("${type.id} has rules", rules.isNotEmpty())
            BotConfig(rules = rules).slashCommands().forEach { assertTrue("${type.id}: /${it.name}", isValidCommandName(it.name)) }
            rules.forEach { r -> assertFalse("${type.id}: unresolved {{answer}} in ${r.name}", catalogJsonContains(r, "{{answer.")) }
        }
    }

    private fun catalogJsonContains(rule: Rule, text: String) =
        de.erikedits.pocketbot.shared.catalog.catalogJson.encodeToString(Rule.serializer(), rule).contains(text)

    @Test
    fun `answers are inserted and switches remove rules`() {
        val economy = catalog.type("economy")!!
        val rules = RulesCompiler.compile(economy, mapOf("currency" to JsonPrimitive("Taler \"Gold\""), "daily_amount" to JsonPrimitive(250), "work_enabled" to JsonPrimitive(false)))
        assertEquals(listOf("/daily", "/konto"), rules.map { it.name })
        val reply = rules[0].actions.filterIsInstance<Action.Reply>().single()
        assertEquals("💰 +250 Taler \"Gold\"! Du hast jetzt {var.geld}.", reply.text)
        assertEquals("250", (rules[0].actions[0] as Action.VarAdd).amount)
    }

    @Test
    fun `custom commands are parsed`() {
        val type = catalog.type("custom_commands")!!
        val rules = RulesCompiler.compile(type, mapOf("custom_commands" to JsonArray(listOf(JsonPrimitive("IP = play.example.de"), JsonPrimitive("kaputt"), JsonPrimitive("/Zeiten = Mo–Fr")))))
        assertEquals(listOf("ip", "zeiten"), rules.map { (it.trigger as Trigger.SlashCommand).name })
        assertEquals("play.example.de", (rules[0].actions[0] as Action.Reply).text)
    }

    @Test
    fun `show_if hides follow-up questions`() {
        val tickets = catalog.type("tickets")!!
        assertTrue(Questions.visible(tickets, emptyMap()).any { it.id == "log_channel" })
        assertFalse(Questions.visible(tickets, mapOf("transcripts" to JsonPrimitive(false))).any { it.id == "log_channel" })
        val roles = catalog.type("roles")!!
        assertTrue(Questions.visible(roles, emptyMap()).any { it.id == "roles" })
        assertFalse(Questions.visible(roles, mapOf("mode" to JsonPrimitive("verify"))).any { it.id == "roles" })
    }

    @Test
    fun `ai answers are sanitized`() {
        val tickets = catalog.type("tickets")!!
        val clean = Questions.sanitizeAll(
            tickets,
            mapOf(
                "max_open" to JsonPrimitive(99),
                "transcripts" to JsonPrimitive("ja"),
                "categories" to JsonPrimitive("Support; Bewerbung"),
                "unknown" to JsonPrimitive("x"),
            ),
        )
        assertNull(clean["max_open"])
        assertEquals(JsonPrimitive(true), clean["transcripts"])
        assertEquals(JsonArray(listOf(JsonPrimitive("Support"), JsonPrimitive("Bewerbung"))), clean["categories"])
        assertFalse("unknown" in clean)
    }

    @Test
    fun `setup with legacy rules round trips and compiles`() {
        val legacy = Rule(name = "alt", trigger = Trigger.SlashCommand("alt"), actions = listOf(Action.Reply("x")))
        val setup = BotSetup.fromLegacy(BotConfig(rules = listOf(legacy))).withType(TypeSelection("fun"))
        val back = BotSetup.fromJson(setup.toJson())
        assertEquals(setup, back)
        val compiled = RulesCompiler.compileSetup(back, catalog)
        assertTrue(compiled.rules.any { it.name == "alt" })
        assertTrue(compiled.rules.any { it.name == "/wuerfel" })
    }
}
