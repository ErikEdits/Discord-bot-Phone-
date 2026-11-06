package de.erikedits.pocketbot.shared.config

/** Ready-made rule packs per purpose (SPEC 2.3 step 9). Everything stays editable afterwards. */
object Templates {
    data class Template(val key: String, val rules: List<Rule>)

    val categories = listOf("moderation", "community", "support", "gaming", "other")

    fun forCategory(key: String, language: String): Template {
        val de = language.startsWith("de")
        fun t(german: String, english: String) = if (de) german else english
        val rules = when (key) {
            "moderation" -> listOf(
                Rule(
                    name = t("Kick-Befehl", "Kick command"),
                    trigger = Trigger.SlashCommand(
                        "kick", t("Wirft ein Mitglied vom Server", "Kicks a member"),
                        listOf(CommandOption("user", t("Wer?", "Who?"), OptionType.USER), CommandOption("grund", t("Warum?", "Why?"), OptionType.TEXT, required = false)),
                    ),
                    conditions = listOf(Condition.IsAdmin),
                    actions = listOf(Action.Kick("{arg.grund}", "arg:user"), Action.Reply(t("👢 {arg.user} wurde gekickt. Grund: {arg.grund}", "👢 {arg.user} was kicked. Reason: {arg.grund}"))),
                    elseActions = listOf(Action.Reply(t("⛔ Nur Admins dürfen das.", "⛔ Admins only."), private = true)),
                ),
                Rule(
                    name = t("Ban-Befehl", "Ban command"),
                    trigger = Trigger.SlashCommand(
                        "ban", t("Bannt ein Mitglied", "Bans a member"),
                        listOf(CommandOption("user", t("Wer?", "Who?"), OptionType.USER), CommandOption("grund", t("Warum?", "Why?"), OptionType.TEXT, required = false)),
                    ),
                    conditions = listOf(Condition.IsAdmin),
                    actions = listOf(Action.Ban("{arg.grund}", "arg:user"), Action.Reply(t("🔨 {arg.user} wurde gebannt.", "🔨 {arg.user} was banned."))),
                    elseActions = listOf(Action.Reply(t("⛔ Nur Admins dürfen das.", "⛔ Admins only."), private = true)),
                ),
                Rule(
                    name = t("Timeout-Befehl", "Timeout command"),
                    trigger = Trigger.SlashCommand(
                        "timeout", t("Schickt ein Mitglied in den Timeout", "Times out a member"),
                        listOf(CommandOption("user", t("Wer?", "Who?"), OptionType.USER), CommandOption("minuten", t("Wie lange (Minuten)?", "How long (minutes)?"), OptionType.NUMBER)),
                    ),
                    conditions = listOf(Condition.IsAdmin),
                    actions = listOf(Action.Timeout("{arg.minuten}", "", "arg:user"), Action.Reply(t("🔇 {arg.user} ist für {arg.minuten} Minuten stumm.", "🔇 {arg.user} is muted for {arg.minuten} minutes."))),
                    elseActions = listOf(Action.Reply(t("⛔ Nur Admins dürfen das.", "⛔ Admins only."), private = true)),
                ),
                Rule(
                    name = t("Einladungslinks löschen", "Delete invite links"),
                    trigger = Trigger.Message("discord.gg/", MatchMode.CONTAINS),
                    conditions = listOf(Condition.NotAdmin),
                    actions = listOf(Action.DeleteMessage, Action.Dm(t("Bitte keine Einladungslinks auf {server}. 🙏", "Please no invite links on {server}. 🙏"))),
                ),
            )
            "community" -> listOf(
                Rule(
                    name = t("Willkommen", "Welcome"),
                    trigger = Trigger.MemberJoin,
                    actions = listOf(Action.Send("", t("👋 Willkommen auf **{server}**, {user.mention}! Du bist Mitglied Nr. {members}.", "👋 Welcome to **{server}**, {user.mention}! You are member #{members}."))),
                ),
                Rule(
                    name = t("XP für Nachrichten", "XP for messages"),
                    trigger = Trigger.Message("", MatchMode.ANY),
                    cooldownSeconds = 30,
                    actions = listOf(Action.VarAdd(VarScope.USER, "xp", "{random:5-15}")),
                ),
                Rule(
                    name = t("Level-Up", "Level up"),
                    trigger = Trigger.Message("", MatchMode.ANY),
                    conditions = listOf(Condition.Variable(VarScope.USER, "xp", CompareOp.GE, "100")),
                    actions = listOf(
                        Action.VarSet(VarScope.USER, "xp", "0"),
                        Action.VarAdd(VarScope.USER, "level", "1"),
                        Action.Reply(t("🎉 {user.mention} ist jetzt Level {var.level}!", "🎉 {user.mention} reached level {var.level}!")),
                    ),
                ),
                Rule(
                    name = t("Level anzeigen", "Show level"),
                    trigger = Trigger.SlashCommand("level", t("Zeigt dein Level", "Shows your level")),
                    actions = listOf(Action.Reply(t("⭐ {user}: Level {var.level} · {var.xp}/100 XP", "⭐ {user}: level {var.level} · {var.xp}/100 XP"))),
                ),
                Rule(
                    name = t("Tägliche Münzen", "Daily coins"),
                    trigger = Trigger.SlashCommand("daily", t("Hol dir deine täglichen Münzen", "Claim your daily coins")),
                    cooldownSeconds = 86_400,
                    actions = listOf(Action.VarAdd(VarScope.USER, "muenzen", "100"), Action.Reply(t("💰 +100 Münzen! Du hast jetzt {var.muenzen}.", "💰 +100 coins! You now have {var.muenzen}."))),
                ),
                Rule(
                    name = t("Kontostand", "Balance"),
                    trigger = Trigger.SlashCommand("muenzen", t("Zeigt deine Münzen", "Shows your coins")),
                    actions = listOf(Action.Reply(t("💰 {user} hat {var.muenzen} Münzen.", "💰 {user} has {var.muenzen} coins."))),
                ),
            )
            "support" -> listOf(
                Rule(
                    name = t("Hilfe", "Help"),
                    trigger = Trigger.SlashCommand("hilfe", t("Zeigt Infos und Hilfe", "Shows info and help")),
                    actions = listOf(
                        Action.Embed(
                            t("Hilfe für {server}", "Help for {server}"),
                            t("Brauchst du Hilfe? Nutze **/support** mit deiner Frage. Das Team meldet sich bei dir.", "Need help? Use **/support** with your question. The team will get back to you."),
                        ),
                    ),
                ),
                Rule(
                    name = t("Support-Anfrage", "Support request"),
                    trigger = Trigger.SlashCommand(
                        "support", t("Schick eine Frage ans Team", "Send a question to the team"),
                        listOf(CommandOption("frage", t("Deine Frage", "Your question"), OptionType.TEXT)),
                    ),
                    cooldownSeconds = 120,
                    actions = listOf(
                        Action.NotifyMe(t("Support von {user} auf {server}: {arg.frage}", "Support from {user} on {server}: {arg.frage}")),
                        Action.Reply(t("📨 Danke! Deine Frage ist beim Team angekommen.", "📨 Thanks! Your question reached the team."), private = true),
                    ),
                ),
                Rule(
                    name = t("Regeln mit Bestätigung", "Rules with confirmation"),
                    trigger = Trigger.SlashCommand("regeln", t("Zeigt die Serverregeln", "Shows the server rules")),
                    actions = listOf(
                        Action.ButtonReply(
                            t("📜 **Regeln**\n1. Sei nett\n2. Kein Spam\n3. Hab Spaß", "📜 **Rules**\n1. Be nice\n2. No spam\n3. Have fun"),
                            t("Gelesen ✅", "Read ✅"), "rules_ok",
                        ),
                    ),
                ),
                Rule(
                    name = t("Regeln bestätigt", "Rules confirmed"),
                    trigger = Trigger.ButtonClick("rules_ok"),
                    actions = listOf(Action.Reply(t("Danke, {user}! 🙌", "Thanks, {user}! 🙌"), private = true)),
                ),
            )
            "gaming" -> listOf(
                Rule(
                    name = t("Würfeln", "Dice"),
                    trigger = Trigger.SlashCommand("wuerfel", t("Würfelt eine Zahl von 1 bis 6", "Rolls a number from 1 to 6")),
                    actions = listOf(Action.Reply(t("🎲 {user} würfelt eine **{random:1-6}**!", "🎲 {user} rolls a **{random:1-6}**!"))),
                ),
                Rule(
                    name = t("Münzwurf", "Coin flip"),
                    trigger = Trigger.SlashCommand("muenzwurf", t("Kopf oder Zahl?", "Heads or tails?")),
                    actions = listOf(Action.Reply(t("🪙 {choice:Kopf|Zahl}!", "🪙 {choice:Heads|Tails}!"))),
                ),
                Rule(
                    name = t("Magische 8-Kugel", "Magic 8 ball"),
                    trigger = Trigger.SlashCommand(
                        "frage", t("Stell der Kugel eine Frage", "Ask the ball a question"),
                        listOf(CommandOption("frage", t("Deine Frage", "Your question"), OptionType.TEXT)),
                    ),
                    actions = listOf(
                        Action.Reply(
                            t(
                                "🎱 *{arg.frage}*\n**{choice:Ja!|Nein.|Vielleicht…|Auf jeden Fall!|Frag später nochmal.|Eher nicht.}**",
                                "🎱 *{arg.frage}*\n**{choice:Yes!|No.|Maybe…|Definitely!|Ask again later.|Rather not.}**",
                            ),
                        ),
                    ),
                ),
            )
            else -> listOf(
                Rule(
                    name = t("Hallo", "Hello"),
                    trigger = Trigger.SlashCommand("hallo", t("Sagt Hallo", "Says hello")),
                    actions = listOf(Action.Reply(t("👋 Hallo {user.mention}!", "👋 Hello {user.mention}!"))),
                ),
            )
        }
        return Template(key, rules)
    }

    /** Adds template rules, skipping slash commands that already exist. */
    fun merge(config: BotConfig, template: Template): BotConfig {
        val existing = config.rules.mapNotNull { (it.trigger as? Trigger.SlashCommand)?.name }.toSet()
        val fresh = template.rules.filter { r -> (r.trigger as? Trigger.SlashCommand)?.name?.let { it !in existing } ?: true }
        return config.copy(rules = config.rules + fresh)
    }
}
