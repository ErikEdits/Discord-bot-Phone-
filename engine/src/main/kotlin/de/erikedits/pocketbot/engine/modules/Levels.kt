package de.erikedits.pocketbot.engine.modules

import de.erikedits.pocketbot.shared.catalog.bool
import de.erikedits.pocketbot.shared.catalog.int
import de.erikedits.pocketbot.shared.catalog.list
import de.erikedits.pocketbot.shared.catalog.str
import kotlinx.serialization.json.JsonObject
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.utils.FileUpload
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** XP, level-ups, rank card, leaderboard, role rewards (SPEC-v2 3). */
class LevelsModule(typeId: String, config: JsonObject) : BotModule(typeId, config) {
    private val lastXp = ConcurrentHashMap<String, Long>()

    companion object {
        /** XP needed to go from [level] to level+1. */
        fun needed(level: Int): Long = 5L * level * level + 50L * level + 100

        /** Level and progress for a total XP amount. */
        fun levelOf(total: Long): Pair<Int, Long> {
            var level = 0
            var rest = total
            while (rest >= needed(level)) { rest -= needed(level); level++ }
            return level to rest
        }
    }

    private fun key(guildId: String, userId: String) = "lvl:${guildId}:$userId"
    private fun xp(guildId: String, userId: String) = ctx.store.get(key(guildId, userId))?.toLongOrNull() ?: 0

    override fun commands(guild: Guild): List<CommandData> {
        val de = Dc.german(guild)
        return listOf(
            Commands.slash("rank", if (de) "Zeigt dein Level" else "Shows your level").addOption(OptionType.USER, "user", if (de) "Wessen Level?" else "Whose level?", false),
            Commands.slash("top", if (de) "Bestenliste des Servers" else "Server leaderboard"),
        )
    }

    override fun onMessage(e: MessageReceivedEvent) {
        if (!e.isFromGuild || e.author.isBot) return
        val k = key(e.guild.id, e.author.id)
        val now = System.currentTimeMillis()
        val cooldown = config.int("cooldown_seconds", 60) * 1000L
        if (now - (lastXp[k] ?: 0) < cooldown) return
        lastXp[k] = now
        val base = config.int("xp_per_message", 15).coerceIn(1, 100)
        val before = xp(e.guild.id, e.author.id)
        val after = before + Random.nextInt((base * 0.7).toInt().coerceAtLeast(1), (base * 1.3).toInt() + 2)
        ctx.store.set(k, after.toString())
        val oldLevel = levelOf(before).first
        val newLevel = levelOf(after).first
        if (newLevel > oldLevel) ctx.execute { runCatching { levelUp(e, newLevel) }.onFailure { warn("level up: ${it.message}") } }
    }

    private fun levelUp(e: MessageReceivedEvent, level: Int) {
        val text = config.str("levelup_message", "🎉 {user.mention} → {level}").replace("{user.mention}", e.author.asMention).replace("{user}", e.author.effectiveName).replace("{level}", level.toString())
        val target = config.str("levelup_channel").takeIf { it.isNotBlank() }?.let { Dc.textChannel(e.guild, it) } ?: e.channel
        if (text.isNotBlank()) target.sendMessage(text.take(2000)).complete()
        config.list("rewards").forEach { line ->
            val (lvl, role) = line.split(':', limit = 2).let { (it.getOrNull(0)?.trim()?.toIntOrNull() ?: return@forEach) to (it.getOrNull(1)?.trim() ?: return@forEach) }
            if (level >= lvl && role.isNotBlank()) {
                val r = Dc.role(e.guild, role) ?: return@forEach
                if (e.member?.roles?.contains(r) == false) e.guild.addRoleToMember(e.author, r).complete()
            }
        }
    }

    private fun ranking(guild: Guild): List<Pair<String, Long>> =
        ctx.store.keys("lvl:${guild.id}:").map { it.substringAfterLast(':') to (ctx.store.get(it)?.toLongOrNull() ?: 0) }.sortedByDescending { it.second }

    override fun onSlash(e: SlashCommandInteractionEvent): Boolean {
        val guild = e.guild ?: return false
        when (e.name) {
            "rank" -> {
                val user: User = e.getOption("user")?.asUser ?: e.user
                e.deferReply().queue()
                ctx.execute {
                    val total = xp(guild.id, user.id)
                    val (level, progress) = levelOf(total)
                    val rank = ranking(guild).indexOfFirst { it.first == user.id }.let { if (it < 0) 0 else it + 1 }
                    val renderer = ctx.renderer
                    if (config.bool("rank_card", true) && renderer != null) {
                        val avatar = runCatching { ctx.http.newCall(Request.Builder().url(user.effectiveAvatarUrl + "?size=256").build()).execute().use { it.body?.bytes() } }.getOrNull()
                        val png = renderer.rankCard(avatar, user.effectiveName, level, progress, needed(level), rank, 0x5865F2)
                        e.hook.sendFiles(FileUpload.fromData(png, "rank.png")).queue()
                    } else {
                        e.hook.sendMessage("⭐ **${user.effectiveName}** · Level $level · $progress/${needed(level)} XP · #$rank").queue()
                    }
                }
            }
            "top" -> {
                val top = ranking(guild).take(10)
                val text = if (top.isEmpty()) Dc.t(guild, "Noch keine XP vergeben.", "No XP yet.") else top.mapIndexed { i, (id, total) ->
                    "${listOf("🥇", "🥈", "🥉").getOrElse(i) { "${i + 1}." }} <@$id> · Level ${levelOf(total).first} ($total XP)"
                }.joinToString("\n")
                e.replyEmbeds(EmbedBuilder().setTitle(Dc.t(guild, "🏆 Bestenliste", "🏆 Leaderboard")).setDescription(text).setColor(0xF0B232).build()).queue()
            }
            else -> return false
        }
        return true
    }

    override fun panel(lang: String): ModulePanel {
        val api = ctx.jda()
        val lines = api?.guildCache?.flatMap { g ->
            listOf("🏆 ${g.name}") + ranking(g).take(5).mapIndexed { i, (id, total) ->
                "  ${i + 1}. ${g.getMemberById(id)?.effectiveName ?: id} · Level ${levelOf(total).first}"
            }
        }.orEmpty()
        return ModulePanel(typeId, lines.ifEmpty { listOf(Dc.t(lang, "Noch keine XP.", "No XP yet.")) })
    }
}
