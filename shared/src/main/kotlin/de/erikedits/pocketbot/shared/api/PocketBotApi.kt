package de.erikedits.pocketbot.shared.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Removes things that look like Discord tokens before text leaves the phone. */
fun stripSecrets(text: String): String =
    text.replace(Regex("[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{20,}"), "[token]")

/** Thrown for every non-2xx answer of the App-API. [code] is the `error` field of the contract. */
class ApiException(val status: Int, val code: String, override val message: String) : Exception(message) {
    val isUnauthorized: Boolean get() = status == 401
    val isBackendOffline: Boolean get() = status == 503 || status == 504 || code == "backend_timeout"
}

/**
 * Client for the PocketBot App-API (Eriks bot, reached through the Cloudflare worker).
 * [sessionToken] is read for every authenticated call.
 */
class PocketBotApi(
    baseUrl: String,
    private val http: OkHttpClient,
    private val sessionToken: () -> String?,
) {
    private val base: HttpUrl = baseUrl.trimEnd('/').toHttpUrl()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** URL the app opens in a Custom Tab to start the Discord login. */
    fun loginUrl(state: String, codeChallenge: String): String =
        base.newBuilder().addPathSegments("auth/login")
            .addQueryParameter("state", state)
            .addQueryParameter("code_challenge", codeChallenge)
            .build().toString()

    suspend fun health(): HealthResponse = get("v1/health", auth = false)
    suspend fun config(): RemoteConfig = get("v1/config", auth = false)
    suspend fun exchange(body: ExchangeRequest): ExchangeResponse = send("POST", "v1/auth/exchange", json.encodeToString(ExchangeRequest.serializer(), body), auth = false)
    suspend fun me(): ApiUser = get("v1/me")
    suspend fun logout(): OkResponse = send("POST", "v1/auth/logout", "{}")
    suspend fun deleteAccount(): OkResponse = send("DELETE", "v1/me", null)
    suspend fun vault(): VaultList = get("v1/vault")
    suspend fun putVault(botId: String, body: VaultPut): OkResponse =
        send("PUT", "v1/vault/$botId", json.encodeToString(VaultPut.serializer(), body))
    suspend fun deleteVault(botId: String): OkResponse = send("DELETE", "v1/vault/$botId", null)

    suspend fun aiInterpret(body: AiRequest): AiJobStarted =
        send("POST", "v1/ai/interpret", json.encodeToString(AiRequest.serializer(), body.copy(text = stripSecrets(body.text))))

    suspend fun aiJob(jobId: String): AiJob = get("v1/ai/jobs/$jobId")

    /** Starts an AI job and waits for the result (polls every 2 s, up to [timeoutMs]). */
    suspend fun aiRun(body: AiRequest, timeoutMs: Long = 90_000, pollMs: Long = 2_000): AiResult {
        val job = aiInterpret(body)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(pollMs)
            val state = aiJob(job.jobId)
            when (state.status) {
                "done" -> return state.result ?: throw ApiException(200, "ai_bad_output", "empty result")
                "failed" -> throw ApiException(200, state.error ?: "ai_unavailable", state.error ?: "AI failed")
            }
        }
        throw ApiException(0, "ai_timeout", "AI took too long")
    }

    private suspend inline fun <reified T> get(path: String, auth: Boolean = true): T = send("GET", path, null, auth)

    private suspend inline fun <reified T> send(method: String, path: String, body: String?, auth: Boolean = true): T {
        val text = execute(method, path, body, auth)
        return json.decodeFromString(text)
    }

    private suspend fun execute(method: String, path: String, body: String?, auth: Boolean): String = withContext(Dispatchers.IO) {
        val url = base.newBuilder().addPathSegments(path).build()
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        if (auth) {
            val token = sessionToken() ?: throw ApiException(401, "unauthorized", "Not logged in")
            builder.header("Authorization", "Bearer $token")
        }
        val requestBody = body?.toRequestBody(jsonType) ?: if (method == "POST" || method == "PUT") "".toRequestBody(jsonType) else null
        builder.method(method, requestBody)
        try {
            http.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val err = runCatching { json.decodeFromString(ApiErrorBody.serializer(), text) }.getOrNull()
                    throw ApiException(response.code, err?.error ?: "http_${response.code}", err?.message ?: "HTTP ${response.code}")
                }
                text
            }
        } catch (e: IOException) {
            throw ApiException(0, "network", e.message ?: "Network error")
        }
    }
}
