package de.erikedits.pocketbot.engine.modules

import de.erikedits.pocketbot.shared.catalog.list
import de.erikedits.pocketbot.shared.catalog.str
import kotlinx.serialization.json.JsonObject
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button

/** Self roles via buttons or a verification button (SPEC-v2 3). */
class RolesModule(typeId: String, config: JsonObject) : BotModule(typeId, config) {
    private val verify get() = config.str("mode", "buttons") == "verify"
    private val roleNames get() = config.list("roles").take(25)

    override fun onReady(api: JDA) = api.guildCache.forEach(::ensurePanel)
    override fun onGuildJoin(guild: Guild) = ensurePanel(guild)

    private fun ensurePanel(guild: Guild) {
        if (ctx.store.get("roles:panel:${guild.id}") != null) return
        ctx.execute { runCatching { sendPanel(guild) }.onFailure { warn("panel: ${it.message}") } }
    }

    private fun sendPanel(guild: Guild): String {
        val ch = Dc.textChannel(guild, config.str("panel_channel", if (verify) "verifizierung" else "rollen")) ?: error("no channel")
        val buttons = if (verify) {
            listOf(Button.success("pbr:verify", Dc.t(guild, "✅ Verifizieren", "✅ Verify")))
        } else {
            roleNames.mapIndexed { i, r -> Button.secondary("pbr:$i", r.take(80)) }
        }
        val msg = ch.sendMessageEmbeds(EmbedBuilder().setDescription(config.str("panel_text").ifBlank { "👇" }.take(4000)).setColor(0x5865F2).build())
            .setComponents(buttons.chunked(5).map { ActionRow.of(it) }).complete()
        ctx.store.set("roles:panel:${guild.id}", msg.id)
        info("Role panel sent to #${ch.name}")
        return ch.name
    }

    override fun onButton(e: ButtonInteractionEvent): Boolean {
        if (!e.componentId.startsWith("pbr:")) return false
        val guild = e.guild ?: return true
        val member = e.member ?: return true
        e.deferReply(true).queue()
        ctx.execute {
            val text = runCatching {
                if (e.componentId == "pbr:verify") {
                    val role = Dc.role(guild, config.str("verify_role", "Verifiziert"))!!
                    guild.addRoleToMember(member, role).complete()
                    Dc.t(guild, "✅ Du bist jetzt verifiziert!", "✅ You are verified now!")
                } else {
                    val name = roleNames.getOrNull(e.componentId.removePrefix("pbr:").toIntOrNull() ?: -1) ?: error("?")
                    val role = Dc.role(guild, name)!!
                    if (role in member.roles) {
                        guild.removeRoleFromMember(member, role).complete()
                        Dc.t(guild, "➖ Rolle **$name** entfernt.", "➖ Removed role **$name**.")
                    } else {
                        guild.addRoleToMember(member, role).complete()
                        Dc.t(guild, "➕ Rolle **$name** bekommen!", "➕ You got **$name**!")
                    }
                }
            }.getOrElse { Dc.t(guild, "⚠️ Das hat nicht geklappt (Rechte des Bots?): ${it.message}", "⚠️ That didn't work (bot permissions?): ${it.message}") }
            e.hook.sendMessage(text).queue()
        }
        return true
    }

    override fun panel(lang: String) = ModulePanel(
        typeId,
        lines = listOf(if (verify) Dc.t(lang, "Verifizierung → ${config.str("verify_role")}", "Verification → ${config.str("verify_role")}") else Dc.t(lang, "Rollen: ", "Roles: ") + roleNames.joinToString()),
        actions = listOf(PanelAction("send_panel", Dc.t(lang, "Panel (neu) senden", "Send panel (again)"), InputKind.GUILD)),
    )

    override fun perform(action: String, item: String?, input: ControlInput, lang: String): String {
        val api = ctx.jda() ?: return Dc.t(lang, "Bot ist offline", "Bot is offline")
        val guild = input.guildId?.let(api::getGuildById) ?: return Dc.t(lang, "Bitte einen Server wählen", "Please choose a server")
        return Dc.t(lang, "Panel gesendet in #", "Panel sent to #") + sendPanel(guild)
    }
}
