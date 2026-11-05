package de.erikedits.pocketbot.shared.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Models of the PocketBot App-API. The contract lives in docs/BACKEND-INTEGRATION.md, section 4.

@Serializable
data class ApiErrorBody(
    val error: String,
    val message: String? = null,
    @SerialName("retry_after") val retryAfter: Long? = null,
)

@Serializable
data class HealthResponse(val ok: Boolean, val version: String? = null, val time: Long? = null)

@Serializable
data class ExchangeRequest(
    @SerialName("login_code") val loginCode: String,
    @SerialName("code_verifier") val codeVerifier: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
data class ApiUser(
    val id: String,
    val username: String,
    @SerialName("global_name") val globalName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_owner") val isOwner: Boolean = false,
    val premium: Boolean = false,
) {
    val displayName: String get() = globalName?.takeIf { it.isNotBlank() } ?: username
}

@Serializable
data class ExchangeResponse(
    @SerialName("session_token") val sessionToken: String,
    @SerialName("expires_at") val expiresAt: Long,
    val user: ApiUser,
)

@Serializable
data class OkResponse(val ok: Boolean = true)

@Serializable
data class LocalizedText(val de: String = "", val en: String = "") {
    fun pick(language: String): String = if (language.startsWith("de")) de.ifBlank { en } else en.ifBlank { de }
}

@Serializable
data class Hoster(
    val id: String,
    val name: String,
    val url: String? = null,
    val status: String? = null,
    val price: LocalizedText = LocalizedText(),
    val desc: LocalizedText = LocalizedText(),
) {
    val comingSoon: Boolean get() = status == "coming_soon"
}

@Serializable
data class RecommendThreshold(val guilds: Int = 5, val members: Int = 500)

@Serializable
data class RemoteConfig(
    @SerialName("min_app_version") val minAppVersion: Int = 1,
    @SerialName("latest_app_version") val latestAppVersion: Int = 1,
    @SerialName("ads_default_on") val adsDefaultOn: Boolean = true,
    @SerialName("donate_url") val donateUrl: String? = null,
    val announcement: String? = null,
    @SerialName("hoster_recommend_threshold") val hosterRecommendThreshold: RecommendThreshold = RecommendThreshold(),
    val hosters: List<Hoster> = DefaultHosters.list,
)

@Serializable
data class VaultItem(
    @SerialName("bot_id") val botId: String,
    val label: String,
    val blob: String,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

@Serializable
data class VaultList(val items: List<VaultItem> = emptyList())

@Serializable
data class VaultPut(val label: String, val blob: String)

/** Used until the backend's /v1/config answers (and as offline fallback). */
object DefaultHosters {
    val list: List<Hoster> = listOf(
        Hoster(
            "premium", "PocketBot Premium", status = "coming_soon",
            price = LocalizedText("Bald verfügbar", "Coming soon"),
            desc = LocalizedText("Dein Bot läuft auf unseren Servern, ganz ohne Handy.", "Your bot runs on our servers, no phone needed."),
        ),
        Hoster(
            "bot-hosting", "bot-hosting.net", "https://bot-hosting.net",
            price = LocalizedText("Kostenlos", "Free"),
            desc = LocalizedText("Kostenloser Hoster speziell für Discord-Bots.", "Free host made for Discord bots."),
        ),
        Hoster(
            "oracle-free", "Oracle Cloud Free Tier", "https://www.oracle.com/cloud/free/",
            price = LocalizedText("Kostenlos", "Free"),
            desc = LocalizedText("Dauerhaft kostenloser Server, Einrichtung etwas aufwendiger.", "Always-free server, a bit more setup."),
        ),
        Hoster(
            "railway", "Railway", "https://railway.com",
            price = LocalizedText("Kleines Gratis-Kontingent", "Small free tier"),
            desc = LocalizedText("Sehr einfach, Bezahlung nach Verbrauch.", "Very easy, pay as you go."),
        ),
        Hoster(
            "hetzner", "Hetzner Cloud", "https://www.hetzner.com/cloud",
            price = LocalizedText("ab ca. 4 €/Monat", "from ~4 €/month"),
            desc = LocalizedText("Stabiler VPS aus Deutschland.", "Reliable VPS from Germany."),
        ),
        Hoster(
            "netcup", "netcup", "https://www.netcup.com",
            price = LocalizedText("ab ca. 4 €/Monat", "from ~4 €/month"),
            desc = LocalizedText("Günstiger VPS aus Deutschland.", "Affordable VPS from Germany."),
        ),
        Hoster(
            "pterodactyl", "Pterodactyl-Bot-Hoster",
            price = LocalizedText("unterschiedlich", "varies"),
            desc = LocalizedText("Viele Bot-Hoster nutzen Pterodactyl, dort läuft der PocketBot-Runner als Java-App.", "Many bot hosts use Pterodactyl; the PocketBot runner runs there as a Java app."),
        ),
    )
}
