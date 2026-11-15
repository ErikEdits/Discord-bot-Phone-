package de.erikedits.pocketbot.shared.catalog

import de.erikedits.pocketbot.shared.config.BotConfig
import de.erikedits.pocketbot.shared.config.Rule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Everything the user chose for one bot (SPEC-v2): bot types + answers, plus rules from the old builder. */
@Serializable
data class BotSetup(
    val format: Int = 2,
    val types: List<TypeSelection> = emptyList(),
    /** Rules from the old WENN→FALLS→DANN builder, kept running (SPEC-v2 6). */
    @SerialName("legacy_rules") val legacyRules: List<Rule> = emptyList(),
) {
    fun toJson(): String = catalogJson.encodeToString(serializer(), this)

    fun withType(selection: TypeSelection): BotSetup =
        copy(types = types.filterNot { it.typeId == selection.typeId } + selection)

    companion object {
        fun fromJson(text: String): BotSetup = catalogJson.decodeFromString(serializer(), text)

        /** Old configs (rule builder) become legacy rules. */
        fun fromLegacy(config: BotConfig): BotSetup = BotSetup(legacyRules = config.rules)
    }
}

@Serializable
data class TypeSelection(
    @SerialName("type_id") val typeId: String,
    @SerialName("type_version") val typeVersion: Int = 1,
    val answers: Answers = emptyMap(),
    val enabled: Boolean = true,
)
