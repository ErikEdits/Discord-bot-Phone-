package de.erikedits.pocketbot.shared.catalog

import de.erikedits.pocketbot.shared.config.Action
import de.erikedits.pocketbot.shared.config.BotConfig
import de.erikedits.pocketbot.shared.config.requiredIntents
import de.erikedits.pocketbot.shared.discord.Permission
import de.erikedits.pocketbot.shared.discord.PrivilegedIntent
import kotlinx.serialization.json.JsonObject

/** Privileged intents and Discord permissions a bot needs for its chosen types (invite link, hints). */
data class Requirements(val intents: Set<PrivilegedIntent>, val permissions: Long) {
    operator fun plus(other: Requirements) = Requirements(intents + other.intents, permissions or other.permissions)

    companion object {
        val NONE = Requirements(emptySet(), Permission.BASIC)

        fun of(setup: BotSetup, catalog: Catalog): Requirements {
            var req = NONE
            setup.types.filter { it.enabled }.forEach { sel ->
                val type = catalog.type(sel.typeId) ?: return@forEach
                if (type.module != "rules") req += forModule(type.module, Questions.resolve(type, sel.answers))
            }
            return req + forRules(RulesCompiler.compileSetup(setup, catalog))
        }

        fun forModule(module: String, c: JsonObject): Requirements {
            val i = mutableSetOf<PrivilegedIntent>()
            var p = Permission.BASIC
            when (module) {
                "tickets" -> p = p or Permission.MANAGE_CHANNELS or Permission.MANAGE_ROLES
                "server_setup" -> {
                    p = p or ADMINISTRATOR
                    if (c.bool("member_role_on_join")) i += PrivilegedIntent.SERVER_MEMBERS
                }
                "moderation" -> {
                    p = p or Permission.KICK_MEMBERS or Permission.BAN_MEMBERS or Permission.MODERATE_MEMBERS or Permission.MANAGE_MESSAGES or Permission.MANAGE_CHANNELS
                    if (c.bool("block_invites") || c.bool("block_links") || c.list("bad_words").isNotEmpty() || c.bool("caps_filter") || c.num("mention_limit", 0.0) > 0) {
                        i += PrivilegedIntent.MESSAGE_CONTENT
                    }
                }
                "welcome" -> {
                    i += PrivilegedIntent.SERVER_MEMBERS
                    p = p or Permission.MANAGE_CHANNELS
                    if (c.str("auto_role").isNotBlank()) p = p or Permission.MANAGE_ROLES
                }
                "levels" -> if (c.list("rewards").isNotEmpty()) p = p or Permission.MANAGE_ROLES
                "roles" -> p = p or Permission.MANAGE_ROLES or Permission.MANAGE_CHANNELS
                "reminders" -> {
                    p = p or Permission.MANAGE_CHANNELS
                    if (c.str("mention", "none") != "none") p = p or MENTION_EVERYONE
                }
                "images" -> p = p or Permission.MANAGE_CHANNELS
                "giveaway", "polls" -> Unit
            }
            return Requirements(i, p)
        }

        fun forRules(config: BotConfig): Requirements {
            var p = Permission.BASIC
            config.rules.filter { it.enabled }.flatMap { it.actions + it.elseActions }.forEach { a ->
                p = p or when (a) {
                    is Action.Kick -> Permission.KICK_MEMBERS
                    is Action.Ban -> Permission.BAN_MEMBERS
                    is Action.Timeout -> Permission.MODERATE_MEMBERS
                    is Action.AddRole, is Action.RemoveRole -> Permission.MANAGE_ROLES
                    Action.DeleteMessage -> Permission.MANAGE_MESSAGES
                    else -> 0L
                }
            }
            return Requirements(config.requiredIntents(), p)
        }

        private const val ADMINISTRATOR = 1L shl 3
        private const val MENTION_EVERYONE = 1L shl 17
    }
}
