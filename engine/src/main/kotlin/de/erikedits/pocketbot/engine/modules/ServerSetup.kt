package de.erikedits.pocketbot.engine.modules

import de.erikedits.pocketbot.shared.catalog.bool
import de.erikedits.pocketbot.shared.catalog.int
import de.erikedits.pocketbot.shared.catalog.list
import de.erikedits.pocketbot.shared.catalog.str
import de.erikedits.pocketbot.shared.discord.PrivilegedIntent
import kotlinx.serialization.json.JsonObject
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import java.util.EnumSet

/**
 * Sets up a server from a preset + answers (SPEC-v2 3). Bots can't create servers anymore,
 * so this fills a server the bot was invited to. Runs only when started from the app.
 */
class ServerSetupModule(typeId: String, config: JsonObject) : BotModule(typeId, config) {
    data class Chan(val name: String, val voice: Boolean = false, val readonly: Boolean = false, val key: String = "")
    data class Cat(val name: String, val channels: List<Chan>)

    override fun requiredIntents() = if (config.bool("member_role_on_join")) setOf(PrivilegedIntent.SERVER_MEMBERS) else emptySet()

    private val de get() = config.str("channel_language", "de") == "de"
    private fun n(de: String, en: String) = if (this.de) de else en

    /** The structure that will be created (also shown as preview in the app). */
    fun structure(): List<Cat> {
        val info = Cat(
            n("📌 Info", "📌 Info"),
            listOf(Chan(n("regeln", "rules"), readonly = true, key = "rules"), Chan(n("willkommen", "welcome"), readonly = true, key = "welcome"), Chan(n("ankündigungen", "announcements"), readonly = config.bool("announcements_readonly", true))),
        )
        val extra = config.list("extra_channels").map { Chan(it) }
        val preset = config.str("preset", "community")
        val chat = when (preset) {
            "gaming" -> listOf(Cat(n("💬 Chat", "💬 Chat"), listOf(Chan(n("allgemein", "general")), Chan(n("mitspieler-suche", "looking-for-group")), Chan("clips"), Chan("memes")) + extra), Cat(n("🎮 Spiele", "🎮 Games"), listOf(Chan(n("spiele-talk", "game-talk")), Chan(n("screenshots", "screenshots")))))
            "streamer" -> listOf(Cat(n("📺 Stream", "📺 Stream"), listOf(Chan(n("stream-start", "going-live"), readonly = true), Chan("clips"), Chan(n("vorschläge", "suggestions")))), Cat(n("💬 Chat", "💬 Chat"), listOf(Chan(n("allgemein", "general")), Chan("memes")) + extra))
            "school" -> listOf(Cat(n("📚 Lernen", "📚 Study"), listOf(Chan(n("hausaufgaben", "homework")), Chan(n("fragen", "questions")), Chan(n("materialien", "materials")), Chan(n("termine", "dates")))), Cat(n("💬 Chat", "💬 Chat"), listOf(Chan(n("allgemein", "general"))) + extra))
            "business" -> listOf(Cat(n("🏢 Team", "🏢 Team"), listOf(Chan(n("allgemein", "general")), Chan(n("projekte", "projects")), Chan(n("ideen", "ideas")), Chan(n("termine", "dates"))) + extra))
            "roleplay" -> listOf(Cat(n("🎭 Roleplay", "🎭 Roleplay"), listOf(Chan(n("charaktere", "characters")), Chan("rp-chat"), Chan("ooc"))), Cat(n("💬 Chat", "💬 Chat"), listOf(Chan(n("allgemein", "general"))) + extra))
            "minimal" -> listOf(Cat(n("💬 Chat", "💬 Chat"), listOf(Chan(n("allgemein", "general"))) + extra))
            else -> listOf(Cat(n("💬 Chat", "💬 Chat"), listOf(Chan(n("allgemein", "general")), Chan(n("vorstellen", "introductions")), Chan(n("bilder", "pictures")), Chan("off-topic")) + extra))
        }
        val voiceName = when (preset) {
            "gaming" -> "Gaming"
            "school" -> n("Lernraum", "Study room")
            "business" -> "Meeting"
            "roleplay" -> "RP"
            "streamer" -> n("Stream-Talk", "Stream talk")
            else -> "Lounge"
        }
        val voice = if (config.bool("include_voice", true)) {
            listOf(Cat(n("🔊 Sprache", "🔊 Voice"), (1..config.int("voice_count", 3).coerceIn(1, 10)).map { Chan("$voiceName $it", voice = true) }))
        } else {
            emptyList()
        }
        return listOf(info) + chat + voice
    }

    private fun roleNames() = config.list("roles").map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(15)

    override fun onMemberJoin(e: GuildMemberJoinEvent) {
        if (!config.bool("member_role_on_join")) return
        val last = roleNames().lastOrNull() ?: return
        ctx.execute {
            runCatching { Dc.role(e.guild, last, create = false)?.let { e.guild.addRoleToMember(e.member, it).complete() } }
                .onFailure { warn("join role: ${it.message}") }
        }
    }

    private fun apply(guild: Guild): String {
        val log = mutableListOf<String>()
        if (config.bool("delete_existing")) {
            guild.channels.forEach { ch -> runCatching { ch.delete().complete() } }
            log += "deleted ${guild.channels.size} channels"
        }
        config.str("server_name").takeIf { it.isNotBlank() }?.let { runCatching { guild.manager.setName(it.take(100)).complete(); log += "renamed" } }

        // Roles: create lowest first so the first one ends up highest.
        val colors = listOf(0xF23F43, 0x5865F2, 0x23A55A, 0xF0B232, 0xEB459E, 0x00A8FC)
        val names = roleNames()
        val roles = mutableMapOf<String, Role>()
        names.withIndex().reversed().forEach { (i, name) ->
            val existing = guild.getRolesByName(name, true).firstOrNull()
            roles[name] = existing ?: guild.createRole().setName(name).setColor(colors[i % colors.size]).setHoisted(i < 2).apply {
                when (i) {
                    0 -> setPermissions(Permission.ADMINISTRATOR)
                    1 -> if (names.size > 2) setPermissions(Permission.KICK_MEMBERS, Permission.BAN_MEMBERS, Permission.MODERATE_MEMBERS, Permission.MESSAGE_MANAGE, Permission.VIEW_AUDIT_LOGS, Permission.VOICE_MOVE_OTHERS, Permission.NICKNAME_MANAGE)
                }
            }.complete()
        }
        val staff = names.take(if (names.size > 2) 2 else 1).mapNotNull { roles[it] }
        log += "${roles.size} roles"

        var created = 0
        structure().forEach { cat ->
            val category = Dc.category(guild, cat.name) ?: return@forEach
            cat.channels.forEach { c ->
                val name = if (c.voice) c.name else Dc.channelName(c.name)
                val exists = category.channels.any { it.name.equals(name, true) }
                if (exists) return@forEach
                val action = if (c.voice) category.createVoiceChannel(name) else category.createTextChannel(name)
                if (c.readonly) {
                    action.addPermissionOverride(guild.publicRole, null, EnumSet.of(Permission.MESSAGE_SEND))
                    staff.forEach { action.addRolePermissionOverride(it.idLong, EnumSet.of(Permission.MESSAGE_SEND), null) }
                }
                val channel = action.complete()
                created++
                val text = when (c.key) {
                    "rules" -> config.str("rules_text")
                    "welcome" -> config.str("welcome_text")
                    else -> ""
                }
                if (text.isNotBlank() && channel is net.dv8tion.jda.api.entities.channel.concrete.TextChannel) {
                    channel.sendMessage(text.take(2000)).complete()
                }
            }
        }
        log += "$created channels"
        info("Server ${guild.name} set up: ${log.joinToString()}")
        return log.joinToString()
    }

    private fun preview(lang: String): List<String> {
        val lines = mutableListOf(Dc.t(lang, "Rollen: ", "Roles: ") + roleNames().joinToString())
        structure().forEach { cat -> lines += cat.name + ": " + cat.channels.joinToString { (if (it.voice) "🔊 " else "#") + it.name } }
        if (config.bool("delete_existing")) lines += Dc.t(lang, "⚠️ Löscht vorher alle Kanäle!", "⚠️ Deletes all channels first!")
        return lines
    }

    override fun panel(lang: String) = ModulePanel(
        typeId,
        lines = preview(lang),
        actions = listOf(
            PanelAction(
                "apply", Dc.t(lang, "Server jetzt einrichten", "Set up server now"), InputKind.GUILD,
                confirm = Dc.t(lang, "Server wirklich einrichten? Kanäle und Rollen werden erstellt.", "Really set up the server? Channels and roles will be created."),
            ),
        ),
    )

    override fun perform(action: String, item: String?, input: ControlInput, lang: String): String {
        val api = ctx.jda() ?: return Dc.t(lang, "Bot ist offline", "Bot is offline")
        if (action != "apply") return "?"
        val guild = input.guildId?.let(api::getGuildById) ?: return Dc.t(lang, "Bitte einen Server wählen", "Please choose a server")
        if (!guild.selfMember.hasPermission(Permission.ADMINISTRATOR)) {
            return Dc.t(lang, "Der Bot braucht Administrator-Rechte auf ${guild.name}.", "The bot needs administrator permission on ${guild.name}.")
        }
        return Dc.t(lang, "Fertig: ", "Done: ") + apply(guild)
    }
}
