package de.erikedits.pocketbot.engine.modules

import de.erikedits.pocketbot.shared.catalog.bool
import de.erikedits.pocketbot.shared.catalog.int
import de.erikedits.pocketbot.shared.catalog.str
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.components.buttons.Button
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Giveaways with a join button and automatic draw (SPEC-v2 3). Survives app restarts. */
class GiveawayModule(typeId: String, config: JsonObject) : BotModule(typeId, config) {
    @Serializable
    data class Giveaway(
        val id: String, val guildId: String, val channelId: String, var messageId: String = "",
        val prize: String, val endsAt: Long, val winners: Int, val entrants: MutableList<String> = mutableListOf(), var ended: Boolean = false,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private fun save(g: Giveaway) = ctx.store.set("gw:${g.id}", json.encodeToString(Giveaway.serializer(), g))
    private fun load(id: String): Giveaway? = ctx.store.get("gw:$id")?.let { runCatching { json.decodeFromString(Giveaway.serializer(), it) }.getOrNull() }
    private fun all() = ctx.store.keys("gw:").mapNotNull { load(it.removePrefix("gw:")) }

    override fun commands(guild: Guild): List<CommandData> {
        val de = Dc.german(guild)
        return listOf(
            Commands.slash("giveaway", if (de) "Startet ein Gewinnspiel" else "Starts a giveaway")
                .addOption(OptionType.STRING, if (de) "preis" else "prize", if (de) "Was gibt es zu gewinnen?" else "What can be won?", true)
                .addOption(OptionType.INTEGER, if (de) "minuten" else "minutes", if (de) "Dauer in Minuten" else "Duration in minutes", false)
                .addOption(OptionType.INTEGER, if (de) "gewinner" else "winners", if (de) "Anzahl Gewinner" else "Number of winners", false),
        )
    }

    override fun onReady(api: JDA) {
        all().filter { !it.ended }.forEach(::schedule)
    }

    private fun schedule(g: Giveaway) {
        val delay = (g.endsAt - System.currentTimeMillis()).coerceAtLeast(1000)
        keep(ctx.scheduler.schedule({ ctx.execute { runCatching { end(g.id) }.onFailure { warn("end: ${it.message}") } } }, delay, TimeUnit.MILLISECONDS))
    }

    private fun embed(g: Giveaway, guild: Guild?, winners: List<String>? = null): MessageEmbed = EmbedBuilder()
        .setTitle("🎉 ${g.prize.take(200)}")
        .setColor(if (winners == null) 0xEB459E else 0x80848E)
        .setDescription(
            if (winners == null) Dc.t(guild, "Klick auf den Knopf, um mitzumachen!\nEndet <t:${g.endsAt / 1000}:R> · ${g.winners} Gewinner · ${g.entrants.size} Teilnehmer", "Click the button to join!\nEnds <t:${g.endsAt / 1000}:R> · ${g.winners} winner(s) · ${g.entrants.size} entries")
            else Dc.t(guild, "Beendet · Gewinner: ", "Ended · winners: ") + winners.joinToString { "<@$it>" }.ifBlank { "–" },
        ).setTimestamp(Instant.ofEpochMilli(g.endsAt)).build()

    override fun onSlash(e: SlashCommandInteractionEvent): Boolean {
        if (e.name != "giveaway") return false
        val guild = e.guild ?: return false
        val managerRole = config.str("manager_role").takeIf { it.isNotBlank() }?.let { Dc.role(guild, it, create = false) }
        val allowed = Dc.isAdmin(e.member) || (managerRole != null && e.member?.roles?.contains(managerRole) == true)
        if (!allowed) {
            e.reply(Dc.t(guild, "⛔ Du darfst keine Giveaways starten.", "⛔ You may not start giveaways.")).setEphemeral(true).queue()
            return true
        }
        val prize = (e.getOption("preis") ?: e.getOption("prize"))?.asString ?: "?"
        val minutes = (e.getOption("minuten") ?: e.getOption("minutes"))?.asLong ?: config.int("default_minutes", 60).toLong()
        val winners = ((e.getOption("gewinner") ?: e.getOption("winners"))?.asInt ?: 1).coerceIn(1, 20)
        val g = Giveaway(UUID.randomUUID().toString().take(8), guild.id, e.channel.id, prize = prize, endsAt = System.currentTimeMillis() + minutes.coerceIn(1, 43_200) * 60_000, winners = winners)
        e.replyEmbeds(embed(g, guild)).addActionRow(Button.success("pbg:${g.id}", config.str("button_label", "🎉").take(80))).queue { hook ->
            hook.retrieveOriginal().queue { msg -> g.messageId = msg.id; save(g) }
        }
        save(g)
        schedule(g)
        info("Giveaway \"$prize\" started")
        return true
    }

    override fun onButton(e: ButtonInteractionEvent): Boolean {
        if (!e.componentId.startsWith("pbg:")) return false
        val g = load(e.componentId.removePrefix("pbg:"))
        if (g == null || g.ended) {
            e.reply(Dc.t(e.guild, "Dieses Giveaway ist vorbei.", "This giveaway has ended.")).setEphemeral(true).queue()
            return true
        }
        val joined = if (e.user.id in g.entrants) { g.entrants.remove(e.user.id); false } else { g.entrants.add(e.user.id); true }
        save(g)
        e.editMessageEmbeds(embed(g, e.guild)).queue()
        e.hook.sendMessage(if (joined) Dc.t(e.guild, "✅ Du machst mit!", "✅ You're in!") else Dc.t(e.guild, "Du bist ausgetreten.", "You left the giveaway.")).setEphemeral(true).queue()
        return true
    }

    private fun end(id: String): String {
        val g = load(id) ?: return "?"
        if (g.ended) return "already"
        g.ended = true
        save(g)
        val winners = g.entrants.shuffled().take(g.winners)
        val api = ctx.jda() ?: return "offline"
        val channel = api.getChannelById(GuildMessageChannel::class.java, g.channelId) ?: return "no channel"
        runCatching { channel.retrieveMessageById(g.messageId).complete().editMessageEmbeds(embed(g, channel.guild, winners)).setComponents().complete() }
        val mention = if (config.bool("ping_winners", true)) winners.joinToString { "<@$it>" } else winners.size.toString()
        channel.sendMessage(
            if (winners.isEmpty()) Dc.t(channel.guild, "😕 Niemand hat bei **${g.prize}** mitgemacht.", "😕 Nobody joined **${g.prize}**.")
            else Dc.t(channel.guild, "🎉 Glückwunsch $mention! Ihr habt **${g.prize}** gewonnen!", "🎉 Congratulations $mention! You won **${g.prize}**!"),
        ).complete()
        info("Giveaway \"${g.prize}\" ended: ${winners.size} winner(s)")
        return winners.size.toString()
    }

    override fun panel(lang: String): ModulePanel {
        val running = all().filter { !it.ended }
        return ModulePanel(
            typeId,
            lines = listOf(Dc.t(lang, "Starte Giveaways in Discord mit /giveaway.", "Start giveaways in Discord with /giveaway.")),
            items = running.map {
                PanelItem(it.id, "🎉 ${it.prize}", Dc.t(lang, "${it.entrants.size} Teilnehmer", "${it.entrants.size} entries"), listOf(PanelAction("end", Dc.t(lang, "Jetzt auslosen", "Draw now"), confirm = Dc.t(lang, "Jetzt auslosen?", "Draw now?"))))
            },
        )
    }

    override fun perform(action: String, item: String?, input: ControlInput, lang: String): String =
        if (action == "end" && item != null) Dc.t(lang, "Ausgelost: ", "Drawn: ") + end(item) else "?"
}
