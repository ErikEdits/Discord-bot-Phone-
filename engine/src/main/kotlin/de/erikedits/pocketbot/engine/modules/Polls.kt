package de.erikedits.pocketbot.engine.modules

import de.erikedits.pocketbot.shared.catalog.bool
import de.erikedits.pocketbot.shared.catalog.str
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import java.util.UUID

/** Polls with buttons and live results (SPEC-v2 3). */
class PollsModule(typeId: String, config: JsonObject) : BotModule(typeId, config) {
    @Serializable
    data class Poll(val question: String, val options: List<String>, val votes: MutableMap<String, Int> = mutableMapOf())

    private val json = Json { ignoreUnknownKeys = true }

    override fun commands(guild: Guild): List<CommandData> {
        val de = Dc.german(guild)
        val cmd = Commands.slash(if (de) "umfrage" else "poll", if (de) "Erstellt eine Umfrage" else "Creates a poll")
            .addOption(OptionType.STRING, if (de) "frage" else "question", if (de) "Die Frage" else "The question", true)
            .addOption(OptionType.STRING, if (de) "optionen" else "options", if (de) "Antworten getrennt mit ; (z.B. Ja; Nein)" else "Answers separated by ; (e.g. Yes; No)", true)
        if (config.str("who", "everyone") == "admins") cmd.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
        return listOf(cmd)
    }

    private fun embed(p: Poll, guild: Guild?): MessageEmbed {
        val total = p.votes.size
        val lines = p.options.mapIndexed { i, o ->
            val n = p.votes.values.count { it == i }
            val pct = if (total == 0) 0 else n * 100 / total
            "**$o**\n${"█".repeat(pct / 10)}${"░".repeat(10 - pct / 10)} $pct % ($n)"
        }
        return EmbedBuilder().setTitle("📊 ${p.question.take(250)}").setDescription(lines.joinToString("\n\n"))
            .setFooter(Dc.t(guild, "$total Stimmen", "$total votes")).setColor(0x5865F2).build()
    }

    override fun onSlash(e: SlashCommandInteractionEvent): Boolean {
        if (e.name != "umfrage" && e.name != "poll") return false
        val question = (e.getOption("frage") ?: e.getOption("question"))?.asString ?: return true
        val options = ((e.getOption("optionen") ?: e.getOption("options"))?.asString ?: "").split(';').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(10)
        if (options.size < 2) {
            e.reply(Dc.t(e.guild, "Bitte mindestens 2 Antworten mit ; trennen.", "Please give at least 2 answers separated by ;.")).setEphemeral(true).queue()
            return true
        }
        val id = UUID.randomUUID().toString().take(8)
        val poll = Poll(question, options)
        ctx.store.set("poll:$id", json.encodeToString(Poll.serializer(), poll))
        val buttons = options.mapIndexed { i, o -> Button.secondary("pbp:$id:$i", o.take(80)) }
        e.replyEmbeds(embed(poll, e.guild)).setComponents(buttons.chunked(5).map { ActionRow.of(it) }).queue()
        return true
    }

    override fun onButton(e: ButtonInteractionEvent): Boolean {
        if (!e.componentId.startsWith("pbp:")) return false
        val (_, id, idx) = e.componentId.split(':').let { Triple(it[0], it.getOrNull(1) ?: "", it.getOrNull(2)?.toIntOrNull() ?: 0) }
        val poll = ctx.store.get("poll:$id")?.let { runCatching { json.decodeFromString(Poll.serializer(), it) }.getOrNull() }
        if (poll == null) {
            e.reply(Dc.t(e.guild, "Umfrage nicht gefunden.", "Poll not found.")).setEphemeral(true).queue()
            return true
        }
        if (e.user.id in poll.votes && !config.bool("allow_change", true)) {
            e.reply(Dc.t(e.guild, "Du hast schon abgestimmt.", "You already voted.")).setEphemeral(true).queue()
            return true
        }
        poll.votes[e.user.id] = idx
        ctx.store.set("poll:$id", json.encodeToString(Poll.serializer(), poll))
        e.editMessageEmbeds(embed(poll, e.guild)).queue()
        return true
    }
}
