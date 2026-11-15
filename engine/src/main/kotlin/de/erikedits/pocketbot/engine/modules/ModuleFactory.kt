package de.erikedits.pocketbot.engine.modules

import de.erikedits.pocketbot.shared.catalog.BotSetup
import de.erikedits.pocketbot.shared.catalog.Catalog
import de.erikedits.pocketbot.shared.catalog.Questions

/** Creates the built-in modules for a bot's chosen types. Types of module "rules" are compiled to rules instead. */
object ModuleFactory {
    fun create(setup: BotSetup, catalog: Catalog, botId: String): List<BotModule> = setup.types.filter { it.enabled }.mapNotNull { sel ->
        val type = catalog.type(sel.typeId) ?: return@mapNotNull null
        val config = Questions.resolve(type, sel.answers)
        when (type.module) {
            "tickets" -> TicketsModule(type.id, config)
            "server_setup" -> ServerSetupModule(type.id, config)
            "images" -> ImagesModule(type.id, config, botId)
            "moderation" -> ModerationModule(type.id, config)
            "welcome" -> WelcomeModule(type.id, config)
            "levels" -> LevelsModule(type.id, config)
            "giveaway" -> GiveawayModule(type.id, config)
            "polls" -> PollsModule(type.id, config)
            "roles" -> RolesModule(type.id, config)
            "reminders" -> RemindersModule(type.id, config)
            else -> null
        }
    }
}
