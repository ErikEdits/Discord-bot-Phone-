package de.erikedits.pocketbot.shared.discord

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class DiscordUserJson(
    val id: String,
    val username: String,
    @SerialName("global_name") val globalName: String? = null,
    val avatar: String? = null,
    val bot: Boolean = false,
)

@Serializable
internal data class DiscordApplicationJson(
    val id: String,
    val name: String? = null,
    val flags: Long = 0,
    @SerialName("bot_public") val botPublic: Boolean = true,
    @SerialName("approximate_guild_count") val approximateGuildCount: Int? = null,
)

@Serializable
internal data class DiscordPartialGuildJson(val id: String)

/** Privileged gateway intents as switched on in the Developer Portal. */
data class IntentStatus(
    val presence: Boolean,
    val serverMembers: Boolean,
    val messageContent: Boolean,
) {
    companion object {
        // https://discord.com/developers/docs/resources/application#application-object-application-flags
        private const val GATEWAY_PRESENCE = 1L shl 12
        private const val GATEWAY_PRESENCE_LIMITED = 1L shl 13
        private const val GATEWAY_GUILD_MEMBERS = 1L shl 14
        private const val GATEWAY_GUILD_MEMBERS_LIMITED = 1L shl 15
        private const val GATEWAY_MESSAGE_CONTENT = 1L shl 18
        private const val GATEWAY_MESSAGE_CONTENT_LIMITED = 1L shl 19

        fun fromFlags(flags: Long) = IntentStatus(
            presence = flags and (GATEWAY_PRESENCE or GATEWAY_PRESENCE_LIMITED) != 0L,
            serverMembers = flags and (GATEWAY_GUILD_MEMBERS or GATEWAY_GUILD_MEMBERS_LIMITED) != 0L,
            messageContent = flags and (GATEWAY_MESSAGE_CONTENT or GATEWAY_MESSAGE_CONTENT_LIMITED) != 0L,
        )
    }
}

enum class PrivilegedIntent { PRESENCE, SERVER_MEMBERS, MESSAGE_CONTENT }

fun IntentStatus.missing(required: Set<PrivilegedIntent>): Set<PrivilegedIntent> = required.filterTo(mutableSetOf()) {
    when (it) {
        PrivilegedIntent.PRESENCE -> !presence
        PrivilegedIntent.SERVER_MEMBERS -> !serverMembers
        PrivilegedIntent.MESSAGE_CONTENT -> !messageContent
    }
}

data class BotIdentity(
    val userId: String,
    val applicationId: String,
    val username: String,
    val avatarUrl: String,
    val intents: IntentStatus,
    val guildCount: Int,
    val isPublic: Boolean,
)

sealed interface TokenCheckResult {
    data class Valid(val bot: BotIdentity) : TokenCheckResult
    data object Invalid : TokenCheckResult
    data object Malformed : TokenCheckResult
    data class Failed(val message: String) : TokenCheckResult
}
