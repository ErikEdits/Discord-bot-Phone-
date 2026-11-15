package de.erikedits.pocketbot.engine.modules

import de.erikedits.pocketbot.shared.catalog.bool
import de.erikedits.pocketbot.shared.catalog.int
import de.erikedits.pocketbot.shared.catalog.list
import de.erikedits.pocketbot.shared.catalog.str
import kotlinx.serialization.json.JsonObject
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.utils.FileUpload
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * Ticket system (SPEC-v2 3): panel with one button per ticket type → private channel for the user
 * and the staff role, close/claim buttons, transcript to a log channel. Open tickets are recognised
 * by the channel topic, so nothing is lost when the app restarts.
 */
class TicketsModule(typeId: String, config: JsonObject) : BotModule(typeId, config) {
    private val categories get() = config.list("categories").ifEmpty { listOf("Support") }.take(25)
    private val viewSend = EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS)

    override fun onReady(api: JDA) = api.guildCache.forEach { ensurePanel(it) }
    override fun onGuildJoin(guild: Guild) = ensurePanel(guild)

    private fun panelKey(guild: Guild) = "tickets:panel:${guild.id}"

    private fun ensurePanel(guild: Guild) {
        if (ctx.store.get(panelKey(guild)) != null) return
        ctx.execute { runCatching { sendPanel(guild) }.onFailure { warn("panel on ${guild.name}: ${it.message}") } }
    }

    private fun sendPanel(guild: Guild): String {
        val channel = Dc.textChannel(guild, config.str("panel_channel", "tickets")) ?: error("no channel")
        val embed = EmbedBuilder()
            .setTitle(config.str("panel_title", "🎫 Support").take(256))
            .setDescription(config.str("panel_text").take(4000).ifBlank { null })
            .setColor(0x5865F2)
            .build()
        val buttons = categories.mapIndexed { i, c -> Button.primary("pbt:open:$i", c.take(80)) }
        val msg = channel.sendMessageEmbeds(embed).setComponents(buttons.chunked(5).map { ActionRow.of(it) }).complete()
        ctx.store.set(panelKey(guild), msg.id)
        info("Ticket panel sent to #${channel.name} on ${guild.name}")
        return channel.name
    }

    private fun topicOf(userId: String, type: String) = "pocketbot-ticket|$userId|$type"
    private fun ticketOwner(ch: TextChannel): String? = ch.topic?.takeIf { it.startsWith("pocketbot-ticket|") }?.split('|')?.getOrNull(1)
    private fun openTickets(guild: Guild) = guild.textChannels.filter { ticketOwner(it) != null }

    private fun isStaff(member: Member?): Boolean {
        if (member == null) return false
        if (Dc.isAdmin(member) || member.hasPermission(Permission.MANAGE_CHANNEL)) return true
        val staff = Dc.role(member.guild, config.str("staff_role", "Support-Team"), create = false) ?: return false
        return staff in member.roles
    }

    override fun onButton(e: ButtonInteractionEvent): Boolean {
        val id = e.componentId
        if (!id.startsWith("pbt:")) return false
        val guild = e.guild ?: return true
        when {
            id.startsWith("pbt:open:") -> {
                e.deferReply(true).queue()
                val type = categories.getOrNull(id.removePrefix("pbt:open:").toIntOrNull() ?: 0) ?: categories.first()
                ctx.execute {
                    val text = runCatching { openTicket(guild, e.member!!, type) }.getOrElse { "⚠️ ${it.message}" }
                    e.hook.sendMessage(text).queue()
                }
            }
            id == "pbt:close" -> {
                val ch = e.channel as? TextChannel ?: return true
                if (ticketOwner(ch) != e.user.id && !isStaff(e.member)) {
                    e.reply(Dc.t(guild, "⛔ Nur der Ersteller oder das Team kann das Ticket schließen.", "⛔ Only the creator or the team can close this ticket.")).setEphemeral(true).queue()
                    return true
                }
                e.reply(Dc.t(guild, "🔒 Ticket wird in 5 Sekunden geschlossen …", "🔒 Closing ticket in 5 seconds …")).queue()
                ctx.execute { runCatching { closeTicket(ch, e.user.name) }.onFailure { warn("close: ${it.message}") } }
            }
            id == "pbt:claim" -> {
                if (!isStaff(e.member)) {
                    e.reply(Dc.t(guild, "⛔ Nur das Team kann Tickets übernehmen.", "⛔ Only the team can claim tickets.")).setEphemeral(true).queue()
                } else {
                    e.reply(Dc.t(guild, "✋ ${e.user.asMention} kümmert sich um dieses Ticket.", "✋ ${e.user.asMention} is handling this ticket.")).queue()
                }
            }
        }
        return true
    }

    private fun openTicket(guild: Guild, member: Member, type: String): String {
        val mine = openTickets(guild).count { ticketOwner(it) == member.id }
        val max = config.int("max_open", 1).coerceIn(1, 10)
        if (mine >= max) return Dc.t(guild, "⚠️ Du hast schon $mine offene(s) Ticket(s).", "⚠️ You already have $mine open ticket(s).")
        val category = Dc.category(guild, config.str("ticket_category", "Tickets"))
        val staff = Dc.role(guild, config.str("staff_role", "Support-Team"))
        val action = (category?.createTextChannel(Dc.channelName("ticket-${member.user.name}")) ?: guild.createTextChannel(Dc.channelName("ticket-${member.user.name}")))
            .setTopic(topicOf(member.id, type))
            .addPermissionOverride(guild.publicRole, null, EnumSet.of(Permission.VIEW_CHANNEL))
            .addMemberPermissionOverride(member.idLong, viewSend, null)
            .addMemberPermissionOverride(guild.selfMember.idLong, EnumSet.copyOf(viewSend).apply { add(Permission.MANAGE_CHANNEL) }, null)
        if (staff != null) action.addRolePermissionOverride(staff.idLong, viewSend, null)
        val channel = action.complete()
        val welcome = config.str("welcome_text", "Hallo {user}!").replace("{user}", member.asMention).replace("{type}", type)
        val ping = if (config.bool("ping_staff", true) && staff != null) staff.asMention + "\n" else ""
        val buttons = mutableListOf(Button.danger("pbt:close", Dc.t(guild, "🔒 Schließen", "🔒 Close")))
        if (config.bool("allow_claim", true)) buttons += Button.secondary("pbt:claim", Dc.t(guild, "✋ Übernehmen", "✋ Claim"))
        channel.sendMessage(ping + welcome).setComponents(ActionRow.of(buttons)).complete()
        info("Ticket \"$type\" opened by ${member.user.name}")
        ctx.notify(Dc.t(guild, "Neues Ticket ($type) von ${member.effectiveName} auf ${guild.name}", "New ticket ($type) from ${member.effectiveName} on ${guild.name}"))
        return Dc.t(guild, "✅ Dein Ticket: ${channel.asMention}", "✅ Your ticket: ${channel.asMention}")
    }

    private fun closeTicket(ch: TextChannel, closedBy: String) {
        if (config.bool("transcripts", true)) {
            runCatching {
                val messages = ch.iterableHistory.takeAsync(1000).get(30, TimeUnit.SECONDS).reversed()
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm")
                val text = buildString {
                    append("Ticket ${ch.name} · ${ch.guild.name} · closed by $closedBy\n\n")
                    messages.forEach { m ->
                        append("[${fmt.format(Date(m.timeCreated.toInstant().toEpochMilli()))}] ${m.author.name}: ${m.contentDisplay}")
                        m.attachments.forEach { append(" [${it.url}]") }
                        append('\n')
                    }
                }
                val log = Dc.textChannel(ch.guild, config.str("log_channel", "ticket-logs"))
                log?.sendMessage("📄 ${ch.name} · ${Dc.t(ch.guild, "geschlossen von", "closed by")} $closedBy")
                    ?.addFiles(FileUpload.fromData(text.toByteArray(), "transcript-${ch.name}.txt"))?.complete()
            }.onFailure { warn("transcript: ${it.message}") }
        }
        ch.delete().queueAfter(5, TimeUnit.SECONDS)
        info("Ticket ${ch.name} closed by $closedBy")
    }

    override fun panel(lang: String): ModulePanel {
        val api = ctx.jda()
        val items = api?.guildCache?.flatMap { g ->
            openTickets(g).map { ch ->
                val type = ch.topic?.split('|')?.getOrNull(2).orEmpty()
                val owner = ticketOwner(ch)?.let { g.getMemberById(it)?.effectiveName ?: it }.orEmpty()
                PanelItem(ch.id, "#${ch.name}", "$type · $owner · ${g.name}", listOf(PanelAction("close", Dc.t(lang, "Schließen", "Close"), confirm = Dc.t(lang, "Ticket schließen?", "Close ticket?"))))
            }
        }.orEmpty()
        return ModulePanel(
            typeId,
            lines = listOf(Dc.t(lang, "${items.size} offene Tickets · Arten: ${categories.joinToString()}", "${items.size} open tickets · types: ${categories.joinToString()}")),
            items = items,
            actions = listOf(PanelAction("send_panel", Dc.t(lang, "Panel (neu) senden", "Send panel (again)"), InputKind.GUILD)),
        )
    }

    override fun perform(action: String, item: String?, input: ControlInput, lang: String): String {
        val api = ctx.jda() ?: return Dc.t(lang, "Bot ist offline", "Bot is offline")
        return when (action) {
            "send_panel" -> {
                val guild = input.guildId?.let(api::getGuildById) ?: api.guildCache.firstOrNull() ?: return Dc.t(lang, "Kein Server", "No server")
                Dc.t(lang, "Panel gesendet in #", "Panel sent to #") + sendPanel(guild)
            }
            "close" -> {
                val ch = item?.let(api::getTextChannelById) ?: return Dc.t(lang, "Ticket nicht gefunden", "Ticket not found")
                ch.sendMessage(Dc.t(ch.guild, "🔒 Ticket wird über die App geschlossen …", "🔒 Ticket is being closed from the app …")).complete()
                closeTicket(ch, "PocketBot-App")
                Dc.t(lang, "Ticket geschlossen", "Ticket closed")
            }
            else -> "?"
        }
    }
}
