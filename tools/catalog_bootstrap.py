import json, os, sys
OUT = sys.argv[1]
os.makedirs(OUT + "/types", exist_ok=True)
def L(de, en): return {"de": de, "en": en}
def q(id, type, de, en, help_de="", help_en="", default=None, options=None, min=None, max=None, show_if=None, group=None):
    d = {"id": id, "type": type, "label": L(de, en)}
    if help_de or help_en: d["help"] = L(help_de, help_en)
    if default is not None: d["default"] = default
    if options: d["options"] = [{"value": v, "label": L(a, b)} for v, a, b in options]
    if min is not None: d["min"] = min
    if max is not None: d["max"] = max
    if show_if: d["show_if"] = show_if
    if group: d["group"] = L(*group)
    return d

types = []
def add(id, module, emoji, category, name, desc, questions, settings=None, rules=None):
    t = {"id": id, "version": 1, "module": module, "name": L(*name), "description": L(*desc), "emoji": emoji, "category": category, "questions": questions}
    if settings: t["settings"] = settings
    if rules: t["rules"] = rules
    with open(f"{OUT}/types/{id}.json", "w", encoding="utf-8") as f:
        json.dump(t, f, ensure_ascii=False, indent=2); f.write("\n")
    types.append({"id": id, "version": 1, "file": f"types/{id}.json", "name": L(*name), "description": L(*desc), "emoji": emoji, "category": category})

# ---------------------------------------------------------------- tickets
add("tickets", "tickets", "🎫", "support",
    ("Ticket-Bot", "Ticket bot"),
    ("Support-Tickets mit Knopf: jeder bekommt einen privaten Kanal mit dem Team.", "Support tickets with a button: everyone gets a private channel with the team."),
    [
        q("categories", "text_list", "Welche Ticket-Arten soll es geben?", "Which ticket types should exist?", "Ein Knopf pro Art, z.B. Support, Bewerbung, Melden.", "One button per type, e.g. Support, Application, Report.", ["Support", "Frage", "Bewerbung"], group=("Ticket-Arten", "Ticket types")),
        q("panel_channel", "channel", "In welchem Kanal steht das Ticket-Panel?", "Which channel shows the ticket panel?", "Wird erstellt, wenn es ihn noch nicht gibt.", "Created if it doesn't exist.", "tickets", group=("Panel", "Panel")),
        q("panel_title", "text", "Überschrift des Panels", "Panel title", default="🎫 Support", group=("Panel", "Panel")),
        q("panel_text", "long_text", "Text im Panel", "Panel text", default="Klick auf einen Knopf, um ein Ticket zu öffnen. Das Team meldet sich bei dir.", group=("Panel", "Panel")),
        q("staff_role", "role", "Welche Rolle ist das Support-Team?", "Which role is the support team?", "Diese Rolle sieht alle Tickets. Wird erstellt, wenn es sie nicht gibt.", "This role sees all tickets. Created if missing.", "Support-Team", group=("Team", "Team")),
        q("ping_staff", "bool", "Team bei neuem Ticket erwähnen?", "Mention the team on new tickets?", default=True, group=("Team", "Team")),
        q("allow_claim", "bool", "„Übernehmen“-Knopf für das Team?", "\"Claim\" button for the team?", default=True, group=("Team", "Team")),
        q("ticket_category", "text", "Name der Discord-Kategorie für Tickets", "Discord category name for tickets", default="Tickets", group=("Ticket-Kanäle", "Ticket channels")),
        q("welcome_text", "long_text", "Erste Nachricht im Ticket", "First message in the ticket", "Platzhalter: {user}, {type}", "Placeholders: {user}, {type}", "Hallo {user}! 👋 Beschreib bitte dein Anliegen ({type}). Das Team meldet sich gleich.", group=("Ticket-Kanäle", "Ticket channels")),
        q("max_open", "number", "Wie viele offene Tickets darf ein Nutzer gleichzeitig haben?", "How many open tickets per user?", default=1, min=1, max=10, group=("Ticket-Kanäle", "Ticket channels")),
        q("transcripts", "bool", "Beim Schließen ein Protokoll (Transkript) speichern?", "Save a transcript when closing?", default=True, group=("Protokoll", "Transcript")),
        q("log_channel", "channel", "In welchen Kanal kommen die Protokolle?", "Which channel gets the transcripts?", default="ticket-logs", show_if={"question": "transcripts", "equals": True}, group=("Protokoll", "Transcript")),
    ])

# ---------------------------------------------------------------- server setup
PRESETS = [("gaming", "Gaming", "Gaming"), ("community", "Community / Freunde", "Community / friends"), ("streamer", "Streamer / Creator", "Streamer / creator"),
           ("school", "Schule / Lernen", "School / study"), ("business", "Firma / Team", "Business / team"), ("roleplay", "Roleplay", "Roleplay"), ("minimal", "Minimal", "Minimal")]
add("server_setup", "server_setup", "🏗️", "server",
    ("Server-Einrichtung", "Server setup"),
    ("Richtet deinen Server automatisch ein: Kategorien, Kanäle, Rollen und Rechte.", "Sets up your server automatically: categories, channels, roles and permissions."),
    [
        q("preset", "choice", "Was für ein Server soll es werden?", "What kind of server is it?", "Daraus entsteht die Grundstruktur.", "This decides the basic structure.", "community", options=PRESETS, group=("Art", "Kind")),
        q("channel_language", "choice", "Sprache der Kanalnamen", "Language of channel names", default="de", options=[("de", "Deutsch", "German"), ("en", "Englisch", "English")], group=("Art", "Kind")),
        q("server_name", "text", "Neuer Servername (leer = nicht ändern)", "New server name (empty = keep)", group=("Art", "Kind")),
        q("roles", "text_list", "Welche Rollen soll es geben? (oberste zuerst)", "Which roles? (highest first)", "Die erste Rolle bekommt Admin-Rechte, die zweite Moderator-Rechte.", "The first role gets admin rights, the second moderator rights.", ["Admin", "Moderator", "Mitglied"], group=("Rollen", "Roles")),
        q("member_role_on_join", "bool", "Neue Mitglieder bekommen automatisch die letzte Rolle?", "New members get the last role automatically?", default=True, group=("Rollen", "Roles")),
        q("include_voice", "bool", "Sprachkanäle anlegen?", "Create voice channels?", default=True, group=("Kanäle", "Channels")),
        q("voice_count", "number", "Wie viele Sprachkanäle?", "How many voice channels?", default=3, min=1, max=10, show_if={"question": "include_voice", "equals": True}, group=("Kanäle", "Channels")),
        q("extra_channels", "text_list", "Zusätzliche Textkanäle", "Extra text channels", "z.B. memes, clips, hausaufgaben", "e.g. memes, clips, homework", [], group=("Kanäle", "Channels")),
        q("announcements_readonly", "bool", "Ankündigungen nur für das Team schreibbar?", "Announcements writable by staff only?", default=True, group=("Kanäle", "Channels")),
        q("rules_text", "long_text", "Text für den Regeln-Kanal", "Text for the rules channel", default="1. Sei respektvoll\n2. Kein Spam oder Werbung\n3. Halte dich an die Discord-Richtlinien", group=("Texte", "Texts")),
        q("welcome_text", "long_text", "Text für den Willkommens-Kanal", "Text for the welcome channel", default="Willkommen auf unserem Server! 🎉 Lies dir bitte die Regeln durch.", group=("Texte", "Texts")),
        q("delete_existing", "bool", "Bestehende Kanäle vorher LÖSCHEN?", "DELETE existing channels first?", "Vorsicht: Nur für neue, leere Server. Kann nicht rückgängig gemacht werden.", "Careful: only for new, empty servers. Cannot be undone.", False, group=("Achtung", "Careful")),
    ])

# ---------------------------------------------------------------- images
add("images", "images", "🖼️", "fun",
    ("Bilder-Bot", "Image bot"),
    ("Schickt Bilder: vom Handy, nach Zeitplan oder auf Befehl (Katzen, Hunde, Memes).", "Sends images: from your phone, on a schedule or on command (cats, dogs, memes)."),
    [
        q("commands", "multi_choice", "Welche Bild-Befehle soll es geben?", "Which image commands?", default=["cats", "dogs", "memes"],
          options=[("cats", "/katze – zufällige Katze", "/cat – random cat"), ("dogs", "/hund – zufälliger Hund", "/dog – random dog"), ("memes", "/meme – zufälliges Meme", "/meme – random meme"), ("foxes", "/fuchs – zufälliger Fuchs", "/fox – random fox")], group=("Befehle", "Commands")),
        q("schedule_enabled", "bool", "Automatisch Bilder nach Zeitplan posten?", "Post images on a schedule?", default=False, group=("Zeitplan", "Schedule")),
        q("schedule_channel", "channel", "In welchen Kanal?", "Which channel?", default="bilder", show_if={"question": "schedule_enabled", "equals": True}, group=("Zeitplan", "Schedule")),
        q("schedule_time", "time", "Um welche Uhrzeit (täglich)?", "At what time (daily)?", default="18:00", show_if={"question": "schedule_enabled", "equals": True}, group=("Zeitplan", "Schedule")),
        q("schedule_source", "choice", "Welche Bilder?", "Which images?", "„Meine Sammlung“ = Bilder, die du in der App hinzufügst.", "\"My collection\" = images you add in the app.", "collection",
          options=[("collection", "Meine Sammlung (vom Handy)", "My collection (from phone)"), ("cats", "Katzen", "Cats"), ("dogs", "Hunde", "Dogs"), ("memes", "Memes", "Memes")], show_if={"question": "schedule_enabled", "equals": True}, group=("Zeitplan", "Schedule")),
        q("schedule_text", "text", "Text zum Bild (optional)", "Text with the image (optional)", default="📸 Bild des Tages", show_if={"question": "schedule_enabled", "equals": True}, group=("Zeitplan", "Schedule")),
    ])

# ---------------------------------------------------------------- moderation
add("moderation", "moderation", "🛡️", "moderation",
    ("Moderations-Bot", "Moderation bot"),
    ("Kick, Ban, Timeout, Verwarnungen, Nachrichten löschen und ein Automod-Filter.", "Kick, ban, timeout, warnings, clearing messages and an automod filter."),
    [
        q("commands", "multi_choice", "Welche Befehle für Moderatoren?", "Which commands for moderators?", default=["kick", "ban", "timeout", "warn", "clear"],
          options=[("kick", "/kick", "/kick"), ("ban", "/ban", "/ban"), ("timeout", "/timeout", "/timeout"), ("warn", "/warn + /verwarnungen", "/warn + /warnings"), ("clear", "/clear (Nachrichten löschen)", "/clear (delete messages)")], group=("Befehle", "Commands")),
        q("log_channel", "channel", "Kanal für das Moderations-Protokoll", "Channel for the moderation log", default="mod-log", group=("Befehle", "Commands")),
        q("auto_timeout_warns", "number", "Nach wie vielen Verwarnungen automatisch 1 Stunde Timeout? (0 = nie)", "Automatic 1 hour timeout after how many warnings? (0 = never)", default=3, min=0, max=10, group=("Befehle", "Commands")),
        q("block_invites", "bool", "Fremde Discord-Einladungen löschen?", "Delete other servers' invite links?", default=True, group=("Automod", "Automod")),
        q("block_links", "bool", "Alle Links löschen (außer vom Team)?", "Delete all links (except staff)?", default=False, group=("Automod", "Automod")),
        q("bad_words", "text_list", "Verbotene Wörter", "Forbidden words", default=[], group=("Automod", "Automod")),
        q("caps_filter", "bool", "Nachrichten mit fast nur GROSSBUCHSTABEN löschen?", "Delete messages that are almost all CAPS?", default=False, group=("Automod", "Automod")),
        q("mention_limit", "number", "Ab wie vielen Erwähnungen ist es Spam? (0 = aus)", "How many mentions count as spam? (0 = off)", default=5, min=0, max=50, group=("Automod", "Automod")),
    ])

# ---------------------------------------------------------------- welcome
add("welcome", "welcome", "👋", "community",
    ("Willkommens-Bot", "Welcome bot"),
    ("Begrüßt neue Mitglieder mit Text und Bild, gibt Rollen und verabschiedet.", "Greets new members with text and an image, gives roles and says goodbye."),
    [
        q("channel", "channel", "In welchem Kanal begrüßen?", "Which channel for greetings?", default="willkommen", group=("Begrüßung", "Greeting")),
        q("message", "long_text", "Begrüßungstext", "Greeting text", "Platzhalter: {user.mention}, {user}, {server}, {members}", "Placeholders: {user.mention}, {user}, {server}, {members}", "👋 Willkommen auf **{server}**, {user.mention}! Du bist Mitglied Nr. {members}.", group=("Begrüßung", "Greeting")),
        q("image", "bool", "Begrüßungsbild mit Profilbild erzeugen?", "Generate a welcome image with avatar?", default=True, group=("Bild", "Image")),
        q("image_title", "text", "Überschrift auf dem Bild", "Title on the image", default="Willkommen!", show_if={"question": "image", "equals": True}, group=("Bild", "Image")),
        q("color", "color", "Farbe des Bildes", "Image color", default="#5865F2", show_if={"question": "image", "equals": True}, group=("Bild", "Image")),
        q("auto_role", "role", "Rolle für neue Mitglieder (leer = keine)", "Role for new members (empty = none)", default="", group=("Extras", "Extras")),
        q("dm_enabled", "bool", "Zusätzlich eine private Nachricht schicken?", "Also send a direct message?", default=False, group=("Extras", "Extras")),
        q("dm_text", "long_text", "Text der privaten Nachricht", "Direct message text", default="Hey {user}, schön dass du auf {server} bist! Lies dir bitte die Regeln durch. 🙂", show_if={"question": "dm_enabled", "equals": True}, group=("Extras", "Extras")),
        q("leave_enabled", "bool", "Abschied melden, wenn jemand geht?", "Post a goodbye when someone leaves?", default=False, group=("Abschied", "Goodbye")),
        q("leave_text", "text", "Abschiedstext", "Goodbye text", default="😢 {user} hat den Server verlassen.", show_if={"question": "leave_enabled", "equals": True}, group=("Abschied", "Goodbye")),
    ])

# ---------------------------------------------------------------- levels
add("levels", "levels", "⭐", "community",
    ("Level-Bot", "Level bot"),
    ("XP für Aktivität, Level-Ups, Rangkarte und Bestenliste, Rollen als Belohnung.", "XP for activity, level-ups, rank card and leaderboard, roles as rewards."),
    [
        q("xp_per_message", "number", "Wie viele XP pro Nachricht (ungefähr)?", "How much XP per message (about)?", default=15, min=1, max=100, group=("XP", "XP")),
        q("cooldown_seconds", "number", "Nur alle X Sekunden XP (gegen Spam)", "XP only every X seconds (against spam)", default=60, min=0, max=3600, group=("XP", "XP")),
        q("levelup_message", "long_text", "Nachricht beim Level-Up", "Level-up message", "Platzhalter: {user.mention}, {level}", "Placeholders: {user.mention}, {level}", "🎉 {user.mention} ist jetzt Level **{level}**!", group=("Level-Up", "Level-up")),
        q("levelup_channel", "channel", "Kanal für Level-Ups (leer = gleicher Kanal)", "Channel for level-ups (empty = same channel)", default="", group=("Level-Up", "Level-up")),
        q("rank_card", "bool", "/rank als Bild (Rangkarte)?", "/rank as an image (rank card)?", default=True, group=("Befehle", "Commands")),
        q("rewards", "text_list", "Rollen-Belohnungen (Format „Level: Rollenname“)", "Role rewards (format \"level: role name\")", "z.B. „5: Aktiv“, „20: Stammgast“. Rollen werden erstellt, wenn es sie nicht gibt.", "e.g. \"5: Active\", \"20: Regular\". Roles are created if missing.", ["5: Aktiv", "20: Stammgast"], group=("Belohnungen", "Rewards")),
    ])

# ---------------------------------------------------------------- giveaway
add("giveaway", "giveaway", "🎉", "community",
    ("Giveaway-Bot", "Giveaway bot"),
    ("Gewinnspiele mit Mitmach-Knopf und automatischer Auslosung.", "Giveaways with a join button and automatic draw."),
    [
        q("manager_role", "role", "Wer darf Giveaways starten? (leer = Admins)", "Who may start giveaways? (empty = admins)", default=""),
        q("button_label", "text", "Text auf dem Mitmach-Knopf", "Join button text", default="🎉 Mitmachen"),
        q("default_minutes", "number", "Standard-Dauer in Minuten", "Default duration in minutes", default=60, min=1, max=43200),
        q("ping_winners", "bool", "Gewinner erwähnen?", "Mention winners?", default=True),
    ])

# ---------------------------------------------------------------- polls
add("polls", "polls", "📊", "community",
    ("Umfrage-Bot", "Poll bot"),
    ("Umfragen mit Knöpfen und Live-Ergebnis.", "Polls with buttons and live results."),
    [
        q("who", "choice", "Wer darf Umfragen erstellen?", "Who may create polls?", default="everyone", options=[("everyone", "Alle", "Everyone"), ("admins", "Nur Admins", "Admins only")]),
        q("allow_change", "bool", "Darf man seine Stimme ändern?", "Can people change their vote?", default=True),
    ])

# ---------------------------------------------------------------- roles
add("roles", "roles", "🎭", "community",
    ("Rollen & Verifizierung", "Roles & verification"),
    ("Rollen per Knopf auswählen oder Verifizierung vor dem Schreiben.", "Pick roles with buttons or verify before chatting."),
    [
        q("mode", "choice", "Was soll der Bot machen?", "What should the bot do?", default="buttons", options=[("buttons", "Rollen per Knopf zum Auswählen", "Self-roles with buttons"), ("verify", "Verifizierung (Knopf → Rolle)", "Verification (button → role)")]),
        q("panel_channel", "channel", "In welchem Kanal steht das Panel?", "Which channel shows the panel?", default="rollen"),
        q("panel_text", "long_text", "Text im Panel", "Panel text", default="Wähle deine Rollen mit den Knöpfen aus! 👇"),
        q("roles", "text_list", "Welche Rollen zum Auswählen?", "Which roles to pick?", "Werden erstellt, wenn es sie nicht gibt.", "Created if missing.", ["Gamer", "Künstler", "Musik"], show_if={"question": "mode", "equals": "buttons"}),
        q("verify_role", "role", "Welche Rolle gibt es nach der Verifizierung?", "Which role after verification?", default="Verifiziert", show_if={"question": "mode", "equals": "verify"}),
    ])

# ---------------------------------------------------------------- reminders
add("reminders", "reminders", "⏰", "utility",
    ("Ankündigungs-Bot", "Announcement bot"),
    ("Postet Nachrichten automatisch zu festen Zeiten.", "Posts messages automatically at fixed times."),
    [
        q("channel", "channel", "In welchen Kanal?", "Which channel?", default="ankuendigungen"),
        q("messages", "text_list", "Nachrichten (Format „HH:MM Text“, eine pro Zeile)", "Messages (format \"HH:MM text\", one per line)", default=["09:00 Guten Morgen zusammen! ☀️"]),
        q("days", "multi_choice", "An welchen Tagen?", "On which days?", default=["mon", "tue", "wed", "thu", "fri", "sat", "sun"],
          options=[("mon", "Mo", "Mon"), ("tue", "Di", "Tue"), ("wed", "Mi", "Wed"), ("thu", "Do", "Thu"), ("fri", "Fr", "Fri"), ("sat", "Sa", "Sat"), ("sun", "So", "Sun")]),
        q("mention", "choice", "Wer soll erwähnt werden?", "Who should be mentioned?", default="none", options=[("none", "Niemand", "Nobody"), ("here", "@here", "@here"), ("everyone", "@everyone", "@everyone")]),
    ])

# ---------------------------------------------------------------- rule-based types
def slash(name, desc, actions, options=None, cooldown=0, cond=None, else_actions=None, rname=None):
    r = {"name": rname or "/" + name, "trigger": {"type": "slash", "name": name, "description": desc, "options": options or []}, "actions": actions}
    if cooldown: r["cooldown_seconds"] = cooldown
    if cond: r["conditions"] = cond
    if else_actions: r["else_actions"] = else_actions
    return r
def reply(text, private=False): return {"type": "reply", "text": text, "private": private}

add("fun", "rules", "🎲", "fun",
    ("Spaß & Minispiele", "Fun & mini games"),
    ("Würfeln, Münzwurf, Magische 8-Kugel, Schere-Stein-Papier und mehr.", "Dice, coin flip, magic 8 ball, rock paper scissors and more."),
    [
        q("dice", "bool", "/wuerfel – Würfeln", "/dice – roll a die", default=True),
        q("coin", "bool", "/muenzwurf – Kopf oder Zahl", "/coinflip – heads or tails", default=True),
        q("eightball", "bool", "/frage – Magische 8-Kugel", "/ask – magic 8 ball", default=True),
        q("rps", "bool", "/ssp – Schere, Stein, Papier", "/rps – rock, paper, scissors", default=True),
        q("hug", "bool", "/umarmen – jemanden umarmen", "/hug – hug someone", default=True),
    ],
    rules=[
        {"enabled_if": "dice", "rule": slash("wuerfel", "Würfelt eine Zahl von 1 bis 6", [reply("🎲 {user} würfelt eine **{random:1-6}**!")])},
        {"enabled_if": "coin", "rule": slash("muenzwurf", "Kopf oder Zahl?", [reply("🪙 **{choice:Kopf|Zahl}**!")])},
        {"enabled_if": "eightball", "rule": slash("frage", "Stell der Kugel eine Frage", [reply("🎱 *{arg.frage}*\n**{choice:Ja!|Nein.|Vielleicht…|Auf jeden Fall!|Frag später nochmal.|Eher nicht.}**")], options=[{"name": "frage", "description": "Deine Frage", "type": "TEXT", "required": True}])},
        {"enabled_if": "rps", "rule": slash("ssp", "Schere, Stein, Papier gegen den Bot", [reply("{user}: {arg.wahl} · Bot: **{choice:Schere ✂️|Stein 🪨|Papier 📄}**")], options=[{"name": "wahl", "description": "Schere, Stein oder Papier", "type": "TEXT", "required": True}])},
        {"enabled_if": "hug", "rule": slash("umarmen", "Umarme jemanden", [reply("🤗 {user} umarmt {arg.person}!")], options=[{"name": "person", "description": "Wen?", "type": "USER", "required": True}])},
    ])

add("custom_commands", "rules", "💬", "utility",
    ("Eigene Befehle", "Custom commands"),
    ("Eigene Slash-Befehle mit festen Antworten, z.B. /discord, /zeiten, /ip.", "Your own slash commands with fixed answers, e.g. /discord, /times, /ip."),
    [
        q("custom_commands", "text_list", "Deine Befehle (Format „befehl = Antwort“)", "Your commands (format \"command = answer\")", "z.B. „ip = Unsere Server-IP ist play.example.de“", "e.g. \"ip = Our server IP is play.example.com\"", ["regeln = Bitte lies die Regeln im Regeln-Kanal 📜"]),
        q("private_answers", "bool", "Antworten nur für den Fragenden sichtbar?", "Answers only visible to the asker?", default=False),
    ])

add("info", "rules", "ℹ️", "utility",
    ("Info-Bot", "Info bot"),
    ("Befehle mit Infos zu deinem Server: Regeln, Links, Team.", "Commands with info about your server: rules, links, team."),
    [
        q("rules_text", "long_text", "Text für /regeln", "Text for /rules", default="1. Sei respektvoll\n2. Kein Spam\n3. Hab Spaß"),
        q("links_text", "long_text", "Text für /links (Socials, Webseite …)", "Text for /links (socials, website …)", default="🌐 Webseite: …\n📺 YouTube: …"),
        q("team_text", "long_text", "Text für /team", "Text for /team", default="👑 Admin: …\n🛡️ Moderatoren: …"),
    ],
    rules=[
        {"rule": {"name": "/regeln", "trigger": {"type": "slash", "name": "regeln", "description": "Zeigt die Serverregeln"}, "actions": [{"type": "embed", "title": "📜 Regeln von {server}", "description": "{{answer.rules_text}}", "color": "#5865F2"}]}},
        {"rule": {"name": "/links", "trigger": {"type": "slash", "name": "links", "description": "Zeigt wichtige Links"}, "actions": [{"type": "embed", "title": "🔗 Links", "description": "{{answer.links_text}}", "color": "#23A55A"}]}},
        {"rule": {"name": "/team", "trigger": {"type": "slash", "name": "team", "description": "Zeigt das Team"}, "actions": [{"type": "embed", "title": "👥 Team", "description": "{{answer.team_text}}", "color": "#F0B232"}]}},
        {"rule": {"name": "/serverinfo", "trigger": {"type": "slash", "name": "serverinfo", "description": "Infos zum Server"}, "actions": [reply("🏠 **{server}** · {members} Mitglieder")]}},
    ])

add("economy", "rules", "💰", "fun",
    ("Economy-Bot", "Economy bot"),
    ("Eine Server-Währung: tägliche Belohnung, Kontostand und Arbeiten.", "A server currency: daily reward, balance and work."),
    [
        q("currency", "text", "Wie heißt eure Währung?", "What is your currency called?", default="Münzen"),
        q("daily_amount", "number", "Wie viel gibt es mit /daily (1× am Tag)?", "How much for /daily (once a day)?", default=100, min=1, max=100000),
        q("work_enabled", "bool", "/arbeiten (jede Stunde etwas verdienen)?", "/work (earn something every hour)?", default=True),
    ],
    rules=[
        {"rule": {"name": "/daily", "cooldown_seconds": 86400, "trigger": {"type": "slash", "name": "daily", "description": "Tägliche Belohnung abholen"}, "actions": [{"type": "var_add", "scope": "USER", "name": "geld", "amount": "{{answer.daily_amount}}"}, reply("💰 +{{answer.daily_amount}} {{answer.currency}}! Du hast jetzt {var.geld}.")]}},
        {"rule": {"name": "/konto", "trigger": {"type": "slash", "name": "konto", "description": "Zeigt deinen Kontostand"}, "actions": [reply("💰 {user} hat **{var.geld} {{answer.currency}}**.")]}},
        {"enabled_if": "work_enabled", "rule": {"name": "/arbeiten", "cooldown_seconds": 3600, "trigger": {"type": "slash", "name": "arbeiten", "description": "Arbeite für etwas Geld"}, "actions": [{"type": "var_add", "scope": "USER", "name": "geld", "amount": "{random:10-60}"}, reply("🛠️ {user} hat {choice:Pizza ausgeliefert|Code geschrieben|Rasen gemäht|gestreamt} und hat jetzt {var.geld} {{answer.currency}}.")]}},
    ])

index = {"version": 1, "min_format": 1, "types": types}
with open(f"{OUT}/index.json", "w", encoding="utf-8") as f:
    json.dump(index, f, ensure_ascii=False, indent=2); f.write("\n")
print(len(types), "types")
