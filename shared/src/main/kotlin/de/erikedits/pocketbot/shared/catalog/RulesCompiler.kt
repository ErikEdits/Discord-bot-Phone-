package de.erikedits.pocketbot.shared.catalog

import de.erikedits.pocketbot.shared.config.Action
import de.erikedits.pocketbot.shared.config.BotConfig
import de.erikedits.pocketbot.shared.config.Rule
import de.erikedits.pocketbot.shared.config.Trigger
import de.erikedits.pocketbot.shared.config.isValidCommandName
import de.erikedits.pocketbot.shared.config.sanitizeCommandName
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/** Turns bot types of the module "rules" (and old builder rules) into runnable rules. */
object RulesCompiler {
    private val answerRef = Regex("\\{\\{answer\\.([a-zA-Z0-9_]+)}}")

    fun compile(type: BotType, answers: Answers): List<Rule> {
        val config = Questions.resolve(type, answers)
        val fromTemplates = type.rules.withIndex().filter { (_, t) -> t.enabledIf == null || config.bool(t.enabledIf) }.map { (i, t) ->
            val json = catalogJson.encodeToString(Rule.serializer(), t.rule)
            val filled = answerRef.replace(json) { m ->
                val value = config[m.groupValues[1]]
                val text = when (value) {
                    is JsonArray -> value.mapNotNull { it.text() }.joinToString("\n")
                    null -> ""
                    else -> value.text().orEmpty()
                }
                // Insert as JSON string content (escape quotes, newlines …).
                JsonPrimitive(text).toString().removeSurrounding("\"")
            }
            catalogJson.decodeFromString(Rule.serializer(), filled).copy(id = "${type.id}:$i")
        }
        val custom = config.list("custom_commands").mapNotNull { line ->
            val eq = line.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val name = sanitizeCommandName(line.substring(0, eq).trim().removePrefix("/"))
            val answer = line.substring(eq + 1).trim()
            if (!isValidCommandName(name) || answer.isEmpty()) return@mapNotNull null
            Rule(
                id = "${type.id}:cmd:$name",
                name = "/$name",
                trigger = Trigger.SlashCommand(name, answer.take(90)),
                actions = listOf(Action.Reply(answer, private = config.bool("private_answers"))),
            )
        }
        return fromTemplates + custom
    }

    /** All rules of a setup: compiled rule-types + legacy rules. */
    fun compileSetup(setup: BotSetup, catalog: Catalog): BotConfig {
        val rules = setup.types.filter { it.enabled }.flatMap { sel ->
            val type = catalog.type(sel.typeId)?.takeIf { it.module == "rules" } ?: return@flatMap emptyList()
            compile(type, sel.answers)
        }
        return BotConfig(rules = rules + setup.legacyRules)
    }
}
