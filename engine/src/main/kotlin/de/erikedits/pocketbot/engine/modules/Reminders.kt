package de.erikedits.pocketbot.engine.modules

import de.erikedits.pocketbot.shared.catalog.list
import de.erikedits.pocketbot.shared.catalog.str
import kotlinx.serialization.json.JsonObject
import net.dv8tion.jda.api.JDA
import java.time.LocalDate

/** Posts announcements at fixed times on chosen weekdays (SPEC-v2 3). */
class RemindersModule(typeId: String, config: JsonObject) : BotModule(typeId, config) {
    data class Entry(val time: String, val text: String)

    private val days = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

    fun entries(): List<Entry> = config.list("messages").mapNotNull { line ->
        val m = Regex("^\\s*([01]?\\d|2[0-3]):([0-5]\\d)\\s+(.+)$").find(line)
        if (m != null) Entry("${m.groupValues[1].padStart(2, '0')}:${m.groupValues[2]}", m.groupValues[3].trim()) else line.trim().takeIf { it.isNotEmpty() }?.let { Entry("09:00", it) }
    }

    override fun onReady(api: JDA) {
        val allowed = config.list("days").ifEmpty { days }.toSet()
        entries().forEach { entry ->
            daily(entry.time) {
                val today = days[LocalDate.now().dayOfWeek.value - 1]
                if (today in allowed) send(api, entry.text)
            }
        }
        info("${entries().size} announcement(s) scheduled")
    }

    private fun send(api: JDA, text: String): Int {
        val prefix = when (config.str("mention", "none")) {
            "here" -> "@here "
            "everyone" -> "@everyone "
            else -> ""
        }
        var n = 0
        api.guildCache.forEach { g ->
            runCatching { Dc.textChannel(g, config.str("channel", "ankuendigungen"))?.sendMessage((prefix + text).take(2000))?.complete(); n++ }
                .onFailure { warn("send on ${g.name}: ${it.message}") }
        }
        return n
    }

    override fun panel(lang: String) = ModulePanel(
        typeId,
        lines = listOf(Dc.t(lang, "Kanal: #${config.str("channel")}", "Channel: #${config.str("channel")}")),
        items = entries().mapIndexed { i, e -> PanelItem(i.toString(), "⏰ ${e.time}", e.text, listOf(PanelAction("send_now", Dc.t(lang, "Jetzt senden", "Send now")))) },
    )

    override fun perform(action: String, item: String?, input: ControlInput, lang: String): String {
        val api = ctx.jda() ?: return Dc.t(lang, "Bot ist offline", "Bot is offline")
        val entry = item?.toIntOrNull()?.let { entries().getOrNull(it) } ?: return "?"
        return Dc.t(lang, "Gesendet auf ${send(api, entry.text)} Server(n)", "Sent on ${send(api, entry.text)} server(s)")
    }
}
