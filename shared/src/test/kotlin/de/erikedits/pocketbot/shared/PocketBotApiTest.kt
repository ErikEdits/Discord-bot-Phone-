package de.erikedits.pocketbot.shared

import de.erikedits.pocketbot.shared.api.ApiException
import de.erikedits.pocketbot.shared.api.ExchangeRequest
import de.erikedits.pocketbot.shared.api.PocketBotApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class PocketBotApiTest {
    private lateinit var server: MockWebServer
    private var session: String? = null

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun api() = PocketBotApi(server.url("/").toString(), OkHttpClient()) { session }

    @Test
    fun `login url carries state and challenge`() {
        val url = api().loginUrl("st", "ch")
        assertTrue(url.endsWith("/auth/login?state=st&code_challenge=ch"))
    }

    @Test
    fun `exchange posts contract body`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"session_token":"s1","expires_at":1767225600,"user":{"id":"9","username":"erik","global_name":"Erik","avatar_url":null,"is_owner":true,"premium":false}}""",
            ),
        )
        val res = api().exchange(ExchangeRequest("lc", "ver", "Pixel"))
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/auth/exchange", req.path)
        assertEquals("""{"login_code":"lc","code_verifier":"ver","device_name":"Pixel"}""", req.body.readUtf8())
        assertEquals("s1", res.sessionToken)
        assertEquals("Erik", res.user.displayName)
        assertTrue(res.user.isOwner)
    }

    @Test
    fun `bearer token is sent and errors are parsed`() = runTest {
        session = "tok"
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized","message":"Session expired"}"""))
        try {
            api().me()
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertTrue(e.isUnauthorized)
            assertEquals("unauthorized", e.code)
            assertEquals("Session expired", e.message)
        }
        assertEquals("Bearer tok", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `backend offline from worker`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"backend_offline","message":"offline"}"""))
        try {
            api().config()
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertTrue(e.isBackendOffline)
        }
    }

    @Test
    fun `relay timeout counts as offline`() = runTest {
        server.enqueue(MockResponse().setResponseCode(504).setBody("""{"error":"backend_timeout","message":"slow"}"""))
        try {
            api().health()
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertTrue(e.isBackendOffline)
            assertEquals("backend_timeout", e.code)
        }
    }

    @Test
    fun `ai job is started, polled and tokens are stripped`() = runTest {
        session = "tok"
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"job_id":"ai_1","remaining_today":9}"""))
        server.enqueue(MockResponse().setBody("""{"status":"pending"}"""))
        server.enqueue(MockResponse().setBody("""{"status":"done","result":{"types":["tickets"],"answers":{"tickets":{"max_open":2}},"summary":"ok"}}"""))
        val fakeToken = listOf("A".repeat(24), "B".repeat(6), "c".repeat(30)).joinToString(".")
        val result = api().aiRun(de.erikedits.pocketbot.shared.api.AiRequest("setup", "Ticket-Bot bitte $fakeToken", "de"), pollMs = 1)
        assertEquals(listOf("tickets"), result.types)
        assertEquals("2", result.answers["tickets"]!!["max_open"].toString())
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("[token]"))
        assertTrue(!body.contains(fakeToken))
        assertEquals("/v1/ai/jobs/ai_1", server.takeRequest().path)
    }

    @Test
    fun `ai failure becomes an ApiException`() = runTest {
        session = "tok"
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"job_id":"ai_2"}"""))
        server.enqueue(MockResponse().setBody("""{"status":"failed","error":"ai_unavailable"}"""))
        try {
            api().aiRun(de.erikedits.pocketbot.shared.api.AiRequest("setup", "x", "de"), pollMs = 1)
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals("ai_unavailable", e.code)
        }
    }

    @Test
    fun `config falls back to defaults for missing fields`() = runTest {
        server.enqueue(MockResponse().setBody("""{"min_app_version":2,"unknown":1}"""))
        val cfg = api().config()
        assertEquals(2, cfg.minAppVersion)
        assertEquals(5, cfg.hosterRecommendThreshold.guilds)
        assertTrue(cfg.hosters.any { it.id == "bot-hosting" })
    }
}
