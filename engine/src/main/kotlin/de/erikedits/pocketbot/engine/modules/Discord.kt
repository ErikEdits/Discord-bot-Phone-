package de.erikedits.pocketbot.engine.modules

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.interactions.DiscordLocale

/** Small Discord helpers shared by the modules. */
object Dc {
    fun german(guild: Guild?): Boolean = guild?.locale == DiscordLocale.GERMAN

    fun t(guild: Guild?, de: String, en: String) = if (german(guild)) de else en
    fun t(lang: String, de: String, en: String) = if (lang.startsWith("de")) de else en

    /** Discord channel names: lowercase, no spaces. */
    fun channelName(name: String): String =
        name.trim().lowercase().replace(Regex("\\s+"), "-").replace(Regex("[^\\p{L}\\p{N}_\\-]"), "").take(90).ifBlank { "channel" }

    fun isAdmin(member: Member?): Boolean =
        member != null && (member.hasPermission(Permission.ADMINISTRATOR) || member.hasPermission(Permission.MANAGE_SERVER))

    /** Finds a text channel by ID or name; creates it (optionally in [category]) when [create]. Blocking. */
    fun textChannel(guild: Guild, nameOrId: String, create: Boolean = true, category: Category? = null): TextChannel? {
        val key = nameOrId.trim().removePrefix("#")
        if (key.isEmpty()) return null
        key.toLongOrNull()?.let { id -> guild.getTextChannelById(id)?.let { return it } }
        val wanted = channelName(key)
        guild.textChannels.firstOrNull { it.name == wanted || it.name.equals(key, true) }?.let { return it }
        if (!create) return null
        return (category?.createTextChannel(wanted) ?: guild.createTextChannel(wanted)).complete()
    }

    fun category(guild: Guild, name: String, create: Boolean = true): Category? {
        guild.categories.firstOrNull { it.name.equals(name.trim(), true) }?.let { return it }
        return if (create) guild.createCategory(name.trim().take(100)).complete() else null
    }

    /** Finds a role by ID, mention or name; creates it when [create]. Blocking. */
    fun role(guild: Guild, nameOrId: String, create: Boolean = true): Role? {
        val key = nameOrId.trim().removePrefix("<@&").removeSuffix(">").removePrefix("@")
        if (key.isEmpty()) return null
        key.toLongOrNull()?.let { id -> guild.getRoleById(id)?.let { return it } }
        guild.getRolesByName(key, true).firstOrNull()?.let { return it }
        if (!create) return null
        return guild.createRole().setName(key.take(100)).complete()
    }

    fun parseColor(hex: String, fallback: Int = 0x5865F2): Int = hex.trim().removePrefix("#").toIntOrNull(16) ?: fallback
}
