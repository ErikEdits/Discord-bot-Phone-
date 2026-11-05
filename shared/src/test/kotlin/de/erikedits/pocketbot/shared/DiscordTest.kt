package de.erikedits.pocketbot.shared

import de.erikedits.pocketbot.shared.api.RecommendThreshold
import de.erikedits.pocketbot.shared.discord.BotToken
import de.erikedits.pocketbot.shared.discord.DiscordRest
import de.erikedits.pocketbot.shared.discord.IntentStatus
import de.erikedits.pocketbot.shared.discord.InviteLink
import de.erikedits.pocketbot.shared.discord.PrivilegedIntent
import de.erikedits.pocketbot.shared.discord.TokenCheckResult
import de.erikedits.pocketbot.shared.discord.missing
import de.erikedits.pocketbot.shared.util.HosterAdvice
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiscordTest {
    // Fake token in Discord's shape (not a real one), assembled at runtime so secret scanners don't flag it.
    private val token = listOf("A".repeat(24), "B".repeat(6), "c".repeat(38)).joinToString(".")
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun rest() = DiscordRest(OkHttpClient(), server.url("/api/v10/").toString())

    @Test
    fun `valid token returns identity with intents`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                assertEquals("Bot $token", request.getHeader("Authorization"))
                return when (request.path) {
                    "/api/v10/users/@me" -> MockResponse().setBody("""{"id":"123456789012345678","username":"testbot","avatar":null,"bot":true}""")
                    "/api/v10/applications/@me" -> MockResponse().setBody("""{"id":"123456789012345678","name":"Test","flags":${(1L shl 19) or (1L shl 15)},"bot_public":true,"approximate_guild_count":7}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val result = rest().checkToken("  Bot $token \n")
        assertTrue(result is TokenCheckResult.Valid)
        val bot = (result as TokenCheckResult.Valid).bot
        assertEquals("testbot", bot.username)
        assertEquals(7, bot.guildCount)
        assertTrue(bot.intents.messageContent)
        assertTrue(bot.intents.serverMembers)
        assertFalse(bot.intents.presence)
        assertTrue(bot.avatarUrl.startsWith("https://cdn.discordapp.com/embed/avatars/"))
    }

    @Test
    fun `falls back to counting guilds`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/v10/users/@me" -> MockResponse().setBody("""{"id":"1","username":"b","avatar":"abc"}""")
                "/api/v10/applications/@me" -> MockResponse().setBody("""{"id":"1","flags":0}""")
                "/api/v10/users/@me/guilds?limit=200" -> MockResponse().setBody("""[{"id":"1"},{"id":"2"}]""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        val bot = (rest().checkToken(token) as TokenCheckResult.Valid).bot
        assertEquals(2, bot.guildCount)
        assertEquals("https://cdn.discordapp.com/avatars/1/abc.png?size=128", bot.avatarUrl)
    }

    @Test
    fun `401 means invalid token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"401: Unauthorized","code":0}"""))
        assertEquals(TokenCheckResult.Invalid, rest().checkToken(token))
    }

    @Test
    fun `garbage is rejected without network`() = runTest {
        assertEquals(TokenCheckResult.Malformed, rest().checkToken("hello"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `server error is reported as failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(rest().checkToken(token) is TokenCheckResult.Failed)
    }

    @Test
    fun `missing intents`() {
        val status = IntentStatus.fromFlags(1L shl 18)
        assertEquals(
            setOf(PrivilegedIntent.SERVER_MEMBERS),
            status.missing(setOf(PrivilegedIntent.MESSAGE_CONTENT, PrivilegedIntent.SERVER_MEMBERS)),
        )
    }

    @Test
    fun `invite link and masking`() {
        assertEquals(
            "https://discord.com/oauth2/authorize?client_id=42&scope=bot+applications.commands&permissions=8",
            InviteLink.build("42", 8),
        )
        assertEquals("••••••••cccc", BotToken.masked(token))
    }

    @Test
    fun `hoster advice thresholds`() {
        val t = RecommendThreshold(guilds = 5, members = 500)
        assertFalse(HosterAdvice.shouldRecommend(false, 4, 499, t))
        assertTrue(HosterAdvice.shouldRecommend(false, 5, 10, t))
        assertTrue(HosterAdvice.shouldRecommend(false, 1, 500, t))
        assertTrue(HosterAdvice.shouldRecommend(true, 0, 0, t))
    }
}
