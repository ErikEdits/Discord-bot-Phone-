package de.erikedits.pocketbot.shared.catalog

import de.erikedits.pocketbot.shared.api.LocalizedText
import de.erikedits.pocketbot.shared.config.Rule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Bot-type catalog (SPEC-v2 2): JSON descriptions loaded from GitHub, never code.
 * Each type runs on one built-in module and asks questions whose answers become the module config.
 */
val catalogJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "type"; isLenient = true }

@Serializable
data class CatalogIndex(
    val version: Int = 1,
    /** Lowest app catalog format this index needs. */
    @SerialName("min_format") val minFormat: Int = 1,
    val types: List<CatalogEntry> = emptyList(),
)

@Serializable
data class CatalogEntry(
    val id: String,
    val version: Int = 1,
    val file: String,
    val name: LocalizedText = LocalizedText(),
    val description: LocalizedText = LocalizedText(),
    val emoji: String = "🤖",
    val category: String = "other",
)

@Serializable
data class BotType(
    val id: String,
    val version: Int = 1,
    /** Built-in module that runs this type: tickets, server_setup, images, moderation, welcome, levels, giveaway, polls, roles, reminders, rules. */
    val module: String,
    val name: LocalizedText = LocalizedText(),
    val description: LocalizedText = LocalizedText(),
    val emoji: String = "🤖",
    val category: String = "other",
    val questions: List<Question> = emptyList(),
    /** Constant settings merged under the answers. */
    val settings: JsonObject = JsonObject(emptyMap()),
    /** Only for module "rules": rule templates; texts may contain {{answer.id}}. */
    val rules: List<RuleTemplate> = emptyList(),
)

@Serializable
data class RuleTemplate(
    /** Question id of a bool answer that switches this rule on (optional). */
    @SerialName("enabled_if") val enabledIf: String? = null,
    val rule: Rule,
)

@Serializable
enum class QuestionType {
    @SerialName("text") TEXT,
    @SerialName("long_text") LONG_TEXT,
    @SerialName("text_list") TEXT_LIST,
    @SerialName("choice") CHOICE,
    @SerialName("multi_choice") MULTI_CHOICE,
    @SerialName("bool") BOOL,
    @SerialName("number") NUMBER,
    @SerialName("channel") CHANNEL,
    @SerialName("role") ROLE,
    @SerialName("color") COLOR,
    @SerialName("emoji") EMOJI,
    @SerialName("time") TIME,
}

@Serializable
data class QuestionOption(val value: String, val label: LocalizedText = LocalizedText())

/** Show a question only if another answer has a value (or one of several). */
@Serializable
data class ShowIf(
    val question: String,
    val equals: JsonElement? = null,
    @SerialName("not_equals") val notEquals: JsonElement? = null,
)

@Serializable
data class Question(
    val id: String,
    val type: QuestionType,
    val label: LocalizedText = LocalizedText(),
    val help: LocalizedText = LocalizedText(),
    val options: List<QuestionOption> = emptyList(),
    val default: JsonElement? = null,
    val min: Double? = null,
    val max: Double? = null,
    @SerialName("show_if") val showIf: ShowIf? = null,
    /** Page/group title shown above the question (questions with the same group share a page). */
    val group: LocalizedText? = null,
)

/** Answers of one bot type: question id → value (string, number, bool or list of strings). */
typealias Answers = Map<String, JsonElement>

object Questions {
    fun visible(type: BotType, answers: Answers): List<Question> = type.questions.filter { isVisible(it, type, answers) }

    fun isVisible(q: Question, type: BotType, answers: Answers): Boolean {
        val cond = q.showIf ?: return true
        val other = type.questions.firstOrNull { it.id == cond.question } ?: return true
        if (!isVisible(other, type, answers)) return false
        val value = valueOf(other, answers)
        if (cond.equals != null && !matches(value, cond.equals)) return false
        if (cond.notEquals != null && matches(value, cond.notEquals)) return false
        return true
    }

    private fun matches(value: JsonElement, expected: JsonElement): Boolean = when {
        expected is JsonArray -> expected.any { matches(value, it) }
        value is JsonArray -> value.any { it == expected || it.text() == expected.text() }
        else -> value == expected || value.text() == expected.text()
    }

    /** Answer or default (or an empty value fitting the type). */
    fun valueOf(q: Question, answers: Answers): JsonElement = answers[q.id] ?: q.default ?: when (q.type) {
        QuestionType.BOOL -> JsonPrimitive(false)
        QuestionType.TEXT_LIST, QuestionType.MULTI_CHOICE -> JsonArray(emptyList())
        QuestionType.NUMBER -> JsonPrimitive(q.min ?: 0.0)
        QuestionType.CHOICE -> q.options.firstOrNull()?.let { JsonPrimitive(it.value) } ?: JsonNull
        else -> JsonPrimitive("")
    }

    /**
     * Checks and normalises an answer (e.g. from the AI): wrong types, unknown options and
     * out-of-range numbers are rejected (null).
     */
    fun sanitize(q: Question, value: JsonElement): JsonElement? = when (q.type) {
        QuestionType.BOOL -> (value as? JsonPrimitive)?.let { p -> p.booleanOrNull ?: p.contentOrNull?.lowercase()?.let { it == "true" || it == "ja" || it == "yes" } }?.let(::JsonPrimitive)
        QuestionType.NUMBER -> (value as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.replace(',', '.')?.toDoubleOrNull() }
            ?.takeIf { (q.min == null || it >= q.min) && (q.max == null || it <= q.max) }
            ?.let { if (it % 1.0 == 0.0) JsonPrimitive(it.toLong()) else JsonPrimitive(it) }
        QuestionType.CHOICE -> value.text()?.takeIf { v -> q.options.any { it.value == v } }?.let(::JsonPrimitive)
        QuestionType.MULTI_CHOICE -> (value as? JsonArray)?.mapNotNull { it.text() }?.filter { v -> q.options.any { it.value == v } }?.distinct()?.map(::JsonPrimitive)?.let(::JsonArray)
        QuestionType.TEXT_LIST -> when (value) {
            is JsonArray -> value.mapNotNull { it.text()?.trim()?.take(100) }
            else -> value.text()?.split('\n', ';')?.map { it.trim().take(100) }
        }?.filter { it.isNotEmpty() }?.take(25)?.map(::JsonPrimitive)?.let(::JsonArray)
        QuestionType.COLOR -> value.text()?.trim()?.let { if (it.startsWith("#")) it else "#$it" }?.takeIf { Regex("^#[0-9a-fA-F]{6}$").matches(it) }?.let(::JsonPrimitive)
        QuestionType.TIME -> value.text()?.trim()?.takeIf { Regex("^([01]?\\d|2[0-3]):[0-5]\\d$").matches(it) }?.let(::JsonPrimitive)
        QuestionType.TEXT, QuestionType.EMOJI, QuestionType.CHANNEL, QuestionType.ROLE -> value.text()?.take(200)?.let(::JsonPrimitive)
        QuestionType.LONG_TEXT -> value.text()?.take(1500)?.let(::JsonPrimitive)
    }

    /** Keeps only valid answers for known questions. */
    fun sanitizeAll(type: BotType, answers: Map<String, JsonElement>): Answers =
        answers.mapNotNull { (id, v) -> type.questions.firstOrNull { it.id == id }?.let { q -> sanitize(q, v)?.let { id to it } } }.toMap()

    /** Module configuration: constant settings + all visible answers (with defaults). */
    fun resolve(type: BotType, answers: Answers): JsonObject {
        val map = LinkedHashMap<String, JsonElement>(type.settings)
        visible(type, answers).forEach { map[it.id] = valueOf(it, answers) }
        return JsonObject(map)
    }
}

fun JsonElement.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

/** Reading helpers for module configs. */
fun JsonObject.str(key: String, default: String = ""): String = this[key]?.text() ?: default
fun JsonObject.bool(key: String, default: Boolean = false): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: default
fun JsonObject.num(key: String, default: Double): Double = (this[key] as? JsonPrimitive)?.doubleOrNull ?: default
fun JsonObject.int(key: String, default: Int): Int = num(key, default.toDouble()).toInt()
fun JsonObject.list(key: String): List<String> = (this[key] as? JsonArray)?.mapNotNull { it.text() } ?: this[key]?.text()?.let { listOf(it) }.orEmpty().filter { it.isNotBlank() }

fun LocalizedText.isEmpty() = de.isBlank() && en.isBlank()
