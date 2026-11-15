package de.erikedits.pocketbot.engine.modules

import de.erikedits.pocketbot.shared.catalog.bool
import de.erikedits.pocketbot.shared.catalog.str
import de.erikedits.pocketbot.shared.discord.PrivilegedIntent
import kotlinx.serialization.json.JsonObject
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.utils.FileUpload
import okhttp3.Request

/** Welcome/goodbye with optional generated image, auto role and DM (SPEC-v2 3). */
class WelcomeModule(typeId: String, config: JsonObject) : BotModule(typeId, config) {
    override fun requiredIntents() = setOf(PrivilegedIntent.SERVER_MEMBERS)

    private fun fill(text: String, user: User, guild: Guild) = text
        .replace("{user.mention}", user.asMention).replace("{user}", user.effectiveName)
        .replace("{server}", guild.name).replace("{members}", guild.memberCount.toString())

    private fun avatar(user: User): ByteArray? = runCatching {
        ctx.http.newCall(Request.Builder().url(user.effectiveAvatarUrl + "?size=256").build()).execute().use { it.body?.bytes() }
    }.getOrNull()

    fun greet(guild: Guild, user: User, test: Boolean = false) {
        val channel = Dc.textChannel(guild, config.str("channel", "willkommen")) ?: return
        val text = fill(config.str("message", "👋 {user.mention}"), user, guild) + if (test) " (Test)" else ""
        val renderer = ctx.renderer
        if (config.bool("image", true) && renderer != null) {
            val png = renderer.welcomeCard(avatar(user), config.str("image_title", "Willkommen!"), user.effectiveName, "#${guild.memberCount} · ${guild.name}", Dc.parseColor(config.str("color", "#5865F2")))
            channel.sendMessage(text.take(2000)).addFiles(FileUpload.fromData(png, "welcome.png")).complete()
        } else {
            channel.sendMessage(text.take(2000)).complete()
        }
    }

    override fun onMemberJoin(e: GuildMemberJoinEvent) {
        if (e.user.isBot) return
        ctx.execute {
            runCatching { greet(e.guild, e.user) }.onFailure { warn("greet: ${it.message}") }
            config.str("auto_role").takeIf { it.isNotBlank() }?.let { r ->
                runCatching { Dc.role(e.guild, r)?.let { e.guild.addRoleToMember(e.member, it).complete() } }.onFailure { warn("auto role: ${it.message}") }
            }
            if (config.bool("dm_enabled")) runCatching {
                e.user.openPrivateChannel().flatMap { it.sendMessage(fill(config.str("dm_text"), e.user, e.guild).take(2000)) }.complete()
            }
        }
    }

    override fun onMemberLeave(e: GuildMemberRemoveEvent) {
        if (!config.bool("leave_enabled") || e.user.isBot) return
        ctx.execute {
            runCatching {
                Dc.textChannel(e.guild, config.str("channel", "willkommen"))?.sendMessage(fill(config.str("leave_text"), e.user, e.guild).take(2000))?.complete()
            }
        }
    }

    override fun panel(lang: String) = ModulePanel(
        typeId,
        lines = listOf(Dc.t(lang, "Begrüßung in #${config.str("channel")}", "Greeting in #${config.str("channel")}") + if (config.bool("image", true)) " + 🖼️" else ""),
        actions = listOf(PanelAction("test", Dc.t(lang, "Test-Begrüßung senden", "Send test greeting"), InputKind.GUILD)),
    )

    override fun perform(action: String, item: String?, input: ControlInput, lang: String): String {
        val api = ctx.jda() ?: return Dc.t(lang, "Bot ist offline", "Bot is offline")
        val guild = input.guildId?.let(api::getGuildById) ?: return Dc.t(lang, "Bitte einen Server wählen", "Please choose a server")
        greet(guild, api.selfUser, test = true)
        return Dc.t(lang, "Test gesendet ✅", "Test sent ✅")
    }
}
