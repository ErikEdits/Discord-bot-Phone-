package de.erikedits.pocketbot.engine.modules

import de.erikedits.pocketbot.shared.catalog.bool
import de.erikedits.pocketbot.shared.catalog.list
import de.erikedits.pocketbot.shared.catalog.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.utils.FileUpload
import okhttp3.Request
import java.io.File

/** Image bot (SPEC-v2 3): /katze /hund /meme /fuchs, daily schedule, send from the phone. */
class ImagesModule(typeId: String, config: JsonObject, private val botId: String) : BotModule(typeId, config) {
    private val json = Json { ignoreUnknownKeys = true }

    private data class Source(val key: String, val de: String, val en: String, val descDe: String, val descEn: String)

    private val sources = listOf(
        Source("cats", "katze", "cat", "Zufälliges Katzenbild", "Random cat picture"),
        Source("dogs", "hund", "dog", "Zufälliges Hundebild", "Random dog picture"),
        Source("memes", "meme", "meme", "Zufälliges Meme", "Random meme"),
        Source("foxes", "fuchs", "fox", "Zufälliges Fuchsbild", "Random fox picture"),
    )

    private val enabled get() = config.list("commands").toSet()

    /** Images the user added in the app. */
    fun collectionDir(): File? = ctx.dataDir?.let { File(it, "images/$botId") }

    private fun collection(): List<File> = collectionDir()?.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()

    override fun commands(guild: Guild): List<CommandData> = sources.filter { it.key in enabled }.map {
        if (Dc.german(guild)) Commands.slash(it.de, it.descDe) else Commands.slash(it.en, it.descEn)
    }

    override fun onSlash(e: SlashCommandInteractionEvent): Boolean {
        val source = sources.firstOrNull { (e.name == it.de || e.name == it.en) && it.key in enabled } ?: return false
        e.deferReply().queue()
        ctx.execute {
            val result = runCatching { fetch(source.key) }
            result.onSuccess { (url, title) ->
                e.hook.sendMessageEmbeds(EmbedBuilder().setTitle(title?.take(256)).setImage(url).setColor(0x5865F2).build()).queue()
            }.onFailure {
                e.hook.sendMessage(Dc.t(e.guild, "😕 Gerade kein Bild gefunden, versuch es gleich nochmal.", "😕 No image right now, please try again.")).queue()
                warn("fetch ${source.key}: ${it.message}")
            }
        }
        return true
    }

    /** Returns image URL (+ title) from free APIs. */
    private fun fetch(key: String): Pair<String, String?> {
        fun get(url: String): String = ctx.http.newCall(Request.Builder().url(url).header("User-Agent", "PocketBot").build()).execute().use {
            check(it.isSuccessful) { "HTTP ${it.code}" }
            it.body!!.string()
        }
        return when (key) {
            "cats" -> json.parseToJsonElement(get("https://api.thecatapi.com/v1/images/search")).let { (it as JsonArray)[0].jsonObject["url"]!!.jsonPrimitive.content } to "🐱"
            "dogs" -> json.parseToJsonElement(get("https://dog.ceo/api/breeds/image/random")).jsonObject["message"]!!.jsonPrimitive.content to "🐶"
            "foxes" -> json.parseToJsonElement(get("https://randomfox.ca/floof/")).jsonObject["image"]!!.jsonPrimitive.content to "🦊"
            else -> {
                repeat(5) {
                    val o = json.parseToJsonElement(get("https://meme-api.com/gimme")).jsonObject
                    val nsfw = o["nsfw"]?.jsonPrimitive?.booleanOrNull ?: true
                    val url = o["url"]?.jsonPrimitive?.content
                    if (!nsfw && url != null && Regex("\\.(png|jpe?g|gif|webp)$", RegexOption.IGNORE_CASE).containsMatchIn(url)) {
                        return url to o["title"]?.jsonPrimitive?.content
                    }
                }
                error("no safe meme")
            }
        }
    }

    override fun onReady(api: JDA) {
        if (!config.bool("schedule_enabled")) return
        daily(config.str("schedule_time", "18:00")) { postScheduled(api) }
        info("Daily image at ${config.str("schedule_time", "18:00")}")
    }

    private fun scheduledChannels(api: JDA): List<GuildMessageChannel> =
        api.guildCache.mapNotNull { g -> runCatching { Dc.textChannel(g, config.str("schedule_channel", "bilder")) }.getOrNull() }

    private fun postScheduled(api: JDA): String {
        val text = config.str("schedule_text")
        val source = config.str("schedule_source", "collection")
        var sent = 0
        scheduledChannels(api).forEach { ch ->
            if (source == "collection") {
                val files = collection()
                if (files.isEmpty()) {
                    warn("collection is empty – add images in the app")
                    return "empty"
                }
                val index = (ctx.store.get("images:next")?.toIntOrNull() ?: 0) % files.size
                ctx.store.set("images:next", (index + 1).toString())
                val f = files[index]
                ch.sendMessage(text.ifBlank { " " }.take(2000)).addFiles(FileUpload.fromData(f, f.name)).complete()
            } else {
                val (url, _) = fetch(source)
                ch.sendMessageEmbeds(EmbedBuilder().setDescription(text.ifBlank { null }).setImage(url).build()).complete()
            }
            sent++
        }
        info("Scheduled image posted to $sent channel(s)")
        return sent.toString()
    }

    override fun panel(lang: String): ModulePanel {
        val count = collection().size
        val lines = mutableListOf(Dc.t(lang, "Sammlung: $count Bilder", "Collection: $count images"))
        lines += Dc.t(lang, "Befehle: ", "Commands: ") + sources.filter { it.key in enabled }.joinToString { "/" + (if (lang.startsWith("de")) it.de else it.en) }.ifBlank { "–" }
        if (config.bool("schedule_enabled")) lines += Dc.t(lang, "Täglich um ${config.str("schedule_time")} in #${config.str("schedule_channel")}", "Daily at ${config.str("schedule_time")} in #${config.str("schedule_channel")}")
        val actions = mutableListOf(PanelAction("send_image", Dc.t(lang, "Bild vom Handy senden", "Send image from phone"), InputKind.IMAGE_AND_CHANNEL))
        if (config.bool("schedule_enabled")) actions += PanelAction("post_now", Dc.t(lang, "Geplantes Bild jetzt posten", "Post scheduled image now"))
        return ModulePanel(typeId, lines, emptyList(), actions)
    }

    override fun perform(action: String, item: String?, input: ControlInput, lang: String): String {
        val api = ctx.jda() ?: return Dc.t(lang, "Bot ist offline", "Bot is offline")
        return when (action) {
            "send_image" -> {
                val ch = input.channelId?.let { api.getChannelById(GuildMessageChannel::class.java, it) } ?: return Dc.t(lang, "Bitte einen Kanal wählen", "Please choose a channel")
                val bytes = input.image ?: return Dc.t(lang, "Kein Bild gewählt", "No image chosen")
                val msg = ch.sendFiles(FileUpload.fromData(bytes, input.imageName ?: "bild.jpg"))
                input.text?.takeIf { it.isNotBlank() }?.let { msg.setContent(it.take(2000)) }
                msg.complete()
                Dc.t(lang, "Bild gesendet ✅", "Image sent ✅")
            }
            "post_now" -> Dc.t(lang, "Gepostet in ", "Posted to ") + postScheduled(api) + Dc.t(lang, " Kanal/Kanälen", " channel(s)")
            else -> "?"
        }
    }
}
