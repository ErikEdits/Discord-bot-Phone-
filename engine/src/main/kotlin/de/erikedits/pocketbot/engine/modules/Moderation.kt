package de.erikedits.pocketbot.engine.modules

import de.erikedits.pocketbot.shared.catalog.bool
import de.erikedits.pocketbot.shared.catalog.int
import de.erikedits.pocketbot.shared.catalog.list
import de.erikedits.pocketbot.shared.catalog.num
import de.erikedits.pocketbot.shared.catalog.str
import de.erikedits.pocketbot.shared.discord.PrivilegedIntent
import kotlinx.serialization.json.JsonObject
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** Moderation (SPEC-v2 3): commands, warnings, automod, mod log. */
class ModerationModule(typeId: String, config: JsonObject) : BotModule(typeId, config) {
    private val enabled get() = config.list("commands").toSet()
    private val recent = CopyOnWriteArrayList<String>()
    private val invite = Regex("discord(?:\\.gg|(?:app)?\\.com/invite)/\\S+", RegexOption.IGNORE_CASE)
    private val link = Regex("https?://\\S+", RegexOption.IGNORE_CASE)

    override fun requiredIntents(): Set<PrivilegedIntent> =
        if (config.bool("block_invites") || config.bool("block_links") || config.list("bad_words").isNotEmpty() || config.bool("caps_filter") || config.num("mention_limit", 0.0) > 0) {
            setOf(PrivilegedIntent.MESSAGE_CONTENT)
        } else {
            emptySet()
        }

    override fun commands(guild: Guild): List<CommandData> {
        val de = Dc.german(guild)
        fun t(a: String, b: String) = if (de) a else b
        val list = mutableListOf<CommandData>()
        if ("kick" in enabled) list += Commands.slash("kick", t("Wirft ein Mitglied raus", "Kicks a member"))
            .addOption(OptionType.USER, "user", t("Wer?", "Who?"), true).addOption(OptionType.STRING, "grund", t("Grund", "Reason"), false)
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.KICK_MEMBERS))
        if ("ban" in enabled) list += Commands.slash("ban", t("Bannt ein Mitglied", "Bans a member"))
            .addOption(OptionType.USER, "user", t("Wer?", "Who?"), true).addOption(OptionType.STRING, "grund", t("Grund", "Reason"), false)
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
        if ("timeout" in enabled) list += Commands.slash("timeout", t("Schickt ein Mitglied in den Timeout", "Times out a member"))
            .addOption(OptionType.USER, "user", t("Wer?", "Who?"), true).addOption(OptionType.INTEGER, "minuten", t("Minuten", "Minutes"), true)
            .addOption(OptionType.STRING, "grund", t("Grund", "Reason"), false)
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
        if ("warn" in enabled) {
            list += Commands.slash("warn", t("Verwarnt ein Mitglied", "Warns a member"))
                .addOption(OptionType.USER, "user", t("Wer?", "Who?"), true).addOption(OptionType.STRING, "grund", t("Grund", "Reason"), true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
            list += Commands.slash(t("verwarnungen", "warnings"), t("Zeigt Verwarnungen", "Shows warnings"))
                .addOption(OptionType.USER, "user", t("Wer?", "Who?"), true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
        }
        if ("clear" in enabled) list += Commands.slash("clear", t("Löscht Nachrichten", "Deletes messages"))
            .addOption(OptionType.INTEGER, t("anzahl", "amount"), t("Wie viele (1–100)?", "How many (1–100)?"), true)
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
        return list
    }

    override fun onSlash(e: SlashCommandInteractionEvent): Boolean {
        val guild = e.guild ?: return false
        val name = e.name
        if (name !in setOf("kick", "ban", "timeout", "warn", "verwarnungen", "warnings", "clear")) return false
        e.deferReply(name == "verwarnungen" || name == "warnings" || name == "clear").queue()
        ctx.execute {
            val text = runCatching { handle(e, guild) }.getOrElse { "⚠️ ${it.message}" }
            e.hook.sendMessage(text.take(2000)).queue()
        }
        return true
    }

    private fun handle(e: SlashCommandInteractionEvent, guild: Guild): String {
        val target: User? = e.getOption("user")?.asUser
        val reason = e.getOption("grund")?.asString.orEmpty()
        val targetMember = e.getOption("user")?.asMember
        if (targetMember != null && !guild.selfMember.canInteract(targetMember)) {
            return Dc.t(guild, "⛔ Ich kann ${targetMember.effectiveName} nicht bearbeiten (Rolle zu hoch).", "⛔ I can't act on ${targetMember.effectiveName} (role too high).")
        }
        return when (e.name) {
            "kick" -> {
                guild.kick(target!!).reason(reason.ifBlank { null }).complete()
                log(guild, "👢 Kick", target, e.user, reason)
                Dc.t(guild, "👢 ${target.asMention} wurde gekickt.", "👢 ${target.asMention} was kicked.")
            }
            "ban" -> {
                guild.ban(target!!, 0, TimeUnit.SECONDS).reason(reason.ifBlank { null }).complete()
                log(guild, "🔨 Ban", target, e.user, reason)
                Dc.t(guild, "🔨 ${target.asMention} wurde gebannt.", "🔨 ${target.asMention} was banned.")
            }
            "timeout" -> {
                val minutes = e.getOption("minuten")?.asLong?.coerceIn(1, 40_320) ?: 10
                guild.timeoutFor(target!!, Duration.ofMinutes(minutes)).reason(reason.ifBlank { null }).complete()
                log(guild, "🔇 Timeout $minutes min", target, e.user, reason)
                Dc.t(guild, "🔇 ${target.asMention} ist für $minutes Minuten stumm.", "🔇 ${target.asMention} is muted for $minutes minutes.")
            }
            "warn" -> {
                val key = "mod:warn:${guild.id}:${target!!.id}"
                val list = ctx.store.get(key)?.split('\n')?.filter { it.isNotBlank() }.orEmpty() + reason.replace('\n', ' ')
                ctx.store.set(key, list.joinToString("\n"))
                log(guild, "⚠️ Warn #${list.size}", target, e.user, reason)
                if (config.bool("warn_dm", true)) runCatching {
                    target.openPrivateChannel().flatMap { it.sendMessage(Dc.t(guild, "⚠️ Verwarnung auf ${guild.name}: $reason", "⚠️ Warning on ${guild.name}: $reason")) }.complete()
                }
                val limit = config.int("auto_timeout_warns", 3)
                var extra = ""
                if (limit > 0 && list.size % limit == 0 && targetMember != null) {
                    runCatching { guild.timeoutFor(targetMember, Duration.ofHours(1)).reason("${list.size} warnings").complete() }
                    extra = Dc.t(guild, " → 1 Stunde Timeout", " → 1 hour timeout")
                }
                Dc.t(guild, "⚠️ ${target.asMention} wurde verwarnt (${list.size}).$extra", "⚠️ ${target.asMention} was warned (${list.size}).$extra")
            }
            "verwarnungen", "warnings" -> {
                val list = ctx.store.get("mod:warn:${guild.id}:${target!!.id}")?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
                if (list.isEmpty()) Dc.t(guild, "✅ ${target.name} hat keine Verwarnungen.", "✅ ${target.name} has no warnings.")
                else "⚠️ ${target.name}:\n" + list.mapIndexed { i, r -> "${i + 1}. ${r.ifBlank { "–" }}" }.joinToString("\n")
            }
            "clear" -> {
                val amount = (e.getOption("anzahl") ?: e.getOption("amount"))?.asInt?.coerceIn(1, 100) ?: 10
                val channel = e.channel as? GuildMessageChannel ?: return "?"
                val messages = channel.history.retrievePast(amount).complete()
                channel.purgeMessages(messages)
                log(guild, "🧹 Clear $amount", null, e.user, "#${channel.name}")
                Dc.t(guild, "🧹 ${messages.size} Nachrichten gelöscht.", "🧹 Deleted ${messages.size} messages.")
            }
            else -> "?"
        }
    }

    private fun log(guild: Guild, title: String, target: User?, by: User, reason: String) {
        recent.add(0, "$title · ${target?.name ?: ""} · ${by.name}${if (reason.isNotBlank()) " · $reason" else ""}")
        while (recent.size > 30) recent.removeAt(recent.size - 1)
        runCatching {
            Dc.textChannel(guild, config.str("log_channel", "mod-log"))?.sendMessageEmbeds(
                EmbedBuilder().setTitle(title).setColor(0xF23F43)
                    .setDescription(buildString {
                        target?.let { append("**User:** ${it.asMention} (${it.name})\n") }
                        append("**Mod:** ${by.asMention}\n")
                        if (reason.isNotBlank()) append("**${Dc.t(guild, "Grund", "Reason")}:** $reason")
                    }).build(),
            )?.complete()
        }
    }

    override fun onMessage(e: MessageReceivedEvent) {
        if (!e.isFromGuild || e.author.isBot) return
        val member = e.member ?: return
        if (Dc.isAdmin(member) || member.hasPermission(Permission.MESSAGE_MANAGE)) return
        val content = e.message.contentRaw
        val reason = when {
            config.bool("block_invites", true) && invite.containsMatchIn(content) -> Dc.t(e.guild, "Einladungslinks sind nicht erlaubt", "Invite links are not allowed")
            config.bool("block_links") && link.containsMatchIn(content) -> Dc.t(e.guild, "Links sind nicht erlaubt", "Links are not allowed")
            config.list("bad_words").any { w -> w.isNotBlank() && Regex("(?i)\\b" + Regex.escape(w.trim()) + "\\b").containsMatchIn(content) } -> Dc.t(e.guild, "Bitte achte auf deine Wortwahl", "Please watch your language")
            config.bool("caps_filter") && content.count(Char::isLetter) >= 10 && content.count(Char::isUpperCase) >= content.count(Char::isLetter) * 0.7 -> Dc.t(e.guild, "Bitte nicht so viel GROSS schreiben", "Please don't use so many CAPS")
            config.num("mention_limit", 0.0) > 0 && e.message.mentions.users.size >= config.num("mention_limit", 5.0) -> Dc.t(e.guild, "Zu viele Erwähnungen", "Too many mentions")
            else -> null
        } ?: return
        ctx.execute {
            runCatching {
                e.message.delete().complete()
                e.channel.sendMessage("${e.author.asMention} ⚠️ $reason").complete().delete().queueAfter(6, TimeUnit.SECONDS)
                log(e.guild, "🤖 Automod", e.author, e.jda.selfUser, reason)
            }.onFailure { warn("automod: ${it.message}") }
        }
    }

    override fun panel(lang: String) = ModulePanel(
        typeId,
        lines = listOf(Dc.t(lang, "Letzte Aktionen:", "Recent actions:")) + recent.take(10).ifEmpty { listOf("–") },
    )
}
