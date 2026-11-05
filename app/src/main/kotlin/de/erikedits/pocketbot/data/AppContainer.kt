package de.erikedits.pocketbot.data

import android.content.Context
import de.erikedits.pocketbot.BuildConfig
import de.erikedits.pocketbot.service.BotManager
import de.erikedits.pocketbot.shared.api.PocketBotApi
import de.erikedits.pocketbot.shared.discord.DiscordRest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Hand-wired dependencies (small enough that no DI framework is needed yet). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val prefs = Prefs(context)
    val secure = SecureStore(context)
    val api = PocketBotApi(BuildConfig.POCKETBOT_API_URL, http) { secure.get(AuthRepository.KEY_SESSION) }
    val discord = DiscordRest(http)
    val auth = AuthRepository(api, secure, prefs)
    val bots = BotRepository(context, secure)
    val botManager = BotManager(bots, appScope)

    val apiConfigured: Boolean get() = !BuildConfig.POCKETBOT_API_URL.contains("example.workers.dev")
}
