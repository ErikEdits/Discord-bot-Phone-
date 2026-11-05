package de.erikedits.pocketbot.shared.discord

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** The few Discord REST calls the app makes itself while a bot is being created. */
class DiscordRest(
    private val http: OkHttpClient,
    baseUrl: String = "https://discord.com/api/v10/",
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()
    private val json = Json { ignoreUnknownKeys = true }

    private class Unauthorized : Exception()

    suspend fun checkToken(rawToken: String): TokenCheckResult {
        val token = BotToken.clean(rawToken)
        if (!BotToken.looksValid(token)) return TokenCheckResult.Malformed
        return withContext(Dispatchers.IO) {
            try {
                val user = call(token, "users/@me", DiscordUserJson.serializer())
                val app = call(token, "applications/@me", DiscordApplicationJson.serializer())
                val guilds = app.approximateGuildCount
                    ?: call(token, "users/@me/guilds?limit=200", ListSerializer(DiscordPartialGuildJson.serializer())).size
                TokenCheckResult.Valid(
                    BotIdentity(
                        userId = user.id,
                        applicationId = app.id,
                        username = user.globalName ?: user.username,
                        avatarUrl = avatarUrl(user.id, user.avatar),
                        intents = IntentStatus.fromFlags(app.flags),
                        guildCount = guilds,
                        isPublic = app.botPublic,
                    ),
                )
            } catch (e: Unauthorized) {
                TokenCheckResult.Invalid
            } catch (e: IOException) {
                TokenCheckResult.Failed(e.message ?: "Network error")
            } catch (e: Exception) {
                TokenCheckResult.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun <T> call(token: String, path: String, serializer: KSerializer<T>): T {
        val url = base.resolve(path) ?: error("bad path $path")
        val request = Request.Builder().url(url)
            .header("Authorization", "Bot $token")
            .header("User-Agent", "DiscordBot (https://github.com/ErikEdits/Discord-bot-Phone-, 0.1) PocketBot")
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 401) throw Unauthorized()
            if (!response.isSuccessful) throw IOException("Discord answered HTTP ${response.code}")
            return json.decodeFromString(serializer, response.body?.string().orEmpty())
        }
    }

    companion object {
        fun avatarUrl(userId: String, hash: String?): String =
            if (hash == null) {
                val index = ((userId.toLongOrNull() ?: 0L) shr 22) % 6
                "https://cdn.discordapp.com/embed/avatars/$index.png"
            } else {
                val ext = if (hash.startsWith("a_")) "gif" else "png"
                "https://cdn.discordapp.com/avatars/$userId/$hash.$ext?size=128"
            }
    }
}

object BotToken {
    private val pattern = Regex("^[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{20,}$")

    /** Removes whitespace, quotes and a leading "Bot " people sometimes copy along. */
    fun clean(raw: String): String = raw.trim().trim('"', '\'').removePrefix("Bot ").trim()

    fun looksValid(token: String): Boolean = pattern.matches(token)

    /** For display: only the last 4 characters. */
    fun masked(token: String): String = "••••••••" + token.takeLast(4)
}
