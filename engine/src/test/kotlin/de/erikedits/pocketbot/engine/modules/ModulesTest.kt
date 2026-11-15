package de.erikedits.pocketbot.engine.modules

import de.erikedits.pocketbot.shared.catalog.BotSetup
import de.erikedits.pocketbot.shared.catalog.Catalog
import de.erikedits.pocketbot.shared.catalog.Questions
import de.erikedits.pocketbot.shared.catalog.Requirements
import de.erikedits.pocketbot.shared.catalog.TypeSelection
import de.erikedits.pocketbot.shared.discord.PrivilegedIntent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModulesTest {
    private val dir = File(System.getProperty("user.dir")).resolve("../catalog").canonicalFile
    private val catalog = Catalog.load(dir.resolve("index.json").readText()) { dir.resolve(it).takeIf(File::exists)?.readText() }

    @Test
    fun `every non-rules catalog type gets a module`() {
        val setup = BotSetup(types = catalog.types.keys.map { TypeSelection(it) })
        val modules = ModuleFactory.create(setup, catalog, "bot")
        assertEquals(catalog.types.values.count { it.module != "rules" }, modules.size)
    }

    @Test
    fun `level formula`() {
        assertEquals(0 to 0L, LevelsModule.levelOf(0))
        assertEquals(1 to 0L, LevelsModule.levelOf(100))
        assertEquals(1 to 154L, LevelsModule.levelOf(254))
        assertEquals(2 to 0L, LevelsModule.levelOf(255))
    }

    @Test
    fun `reminder lines are parsed`() {
        val type = catalog.type("reminders")!!
        val m = RemindersModule("reminders", Questions.resolve(type, mapOf("messages" to JsonArray(listOf(JsonPrimitive("7:30 Morgen!"), JsonPrimitive("Ohne Zeit"), JsonPrimitive("25:00 kaputt"))))))
        assertEquals(listOf(RemindersModule.Entry("07:30", "Morgen!"), RemindersModule.Entry("09:00", "Ohne Zeit"), RemindersModule.Entry("09:00", "25:00 kaputt")), m.entries())
    }

    @Test
    fun `server structure follows the answers`() {
        val type = catalog.type("server_setup")!!
        val gaming = ServerSetupModule("server_setup", Questions.resolve(type, mapOf("preset" to JsonPrimitive("gaming"), "voice_count" to JsonPrimitive(2), "extra_channels" to JsonArray(listOf(JsonPrimitive("Minecraft Talk"))))))
        val all = gaming.structure()
        assertTrue(all.first().channels.any { it.key == "rules" })
        assertTrue(all.flatMap { it.channels }.any { it.name == "Minecraft Talk" })
        assertEquals(2, all.flatMap { it.channels }.count { it.voice })
        val noVoice = ServerSetupModule("server_setup", Questions.resolve(type, mapOf("include_voice" to JsonPrimitive(false), "channel_language" to JsonPrimitive("en"))))
        assertTrue(noVoice.structure().none { cat -> cat.channels.any { it.voice } })
        assertTrue(noVoice.structure().first().channels.any { it.name == "rules" })
        assertEquals("minecraft-talk", Dc.channelName("Minecraft Talk"))
    }

    @Test
    fun `requirements of a full setup`() {
        val setup = BotSetup(types = listOf(TypeSelection("welcome"), TypeSelection("moderation"), TypeSelection("tickets")))
        val req = Requirements.of(setup, catalog)
        assertTrue(PrivilegedIntent.SERVER_MEMBERS in req.intents)
        assertTrue(PrivilegedIntent.MESSAGE_CONTENT in req.intents)
        assertTrue(req.permissions and (1L shl 1) != 0L) // kick
        assertTrue(req.permissions and (1L shl 28) != 0L) // manage roles
        val fun0 = Requirements.of(BotSetup(types = listOf(TypeSelection("fun"))), catalog)
        assertTrue(fun0.intents.isEmpty())
    }
}
