# PocketBot-Katalog

Die App lädt diese Dateien von
`https://raw.githubusercontent.com/ErikEdits/Discord-bot-Phone-/main/catalog/`.
Eine Kopie ist in der App eingebaut. **Neue Bot-Typen = neue JSON-Datei + Eintrag in `index.json`**,
kein App-Update nötig, solange ein vorhandenes Modul benutzt wird.
Nach einer Änderung `version` in `index.json` erhöhen, damit Apps neu laden.

## Aufbau
- `index.json`: `version`, `types[]` mit `id`, `version`, `file`, `name {de,en}`, `description`, `emoji`, `category`
- `types/<id>.json`: `id`, `module`, `name`, `description`, `emoji`, `questions[]`, optional `settings` (feste Werte) und `rules` (nur Modul `rules`)

## Fragen
`id`, `type`, `label {de,en}`, optional `help`, `default`, `options[] {value,label}`, `min`, `max`,
`show_if {question, equals | not_equals}`, `group` (Seitentitel).
Typen: `text`, `long_text`, `text_list`, `choice`, `multi_choice`, `bool`, `number`, `channel`, `role`, `color`, `emoji`, `time`.
`channel`/`role`: Name **oder** ID. Gibt es den Kanal/die Rolle nicht, legt der Bot sie an.

## Module und ihre Fragen-IDs
| Modul | liest |
|---|---|
| `tickets` | categories, panel_channel, panel_title, panel_text, staff_role, ping_staff, allow_claim, ticket_category, welcome_text, max_open, transcripts, log_channel |
| `server_setup` | preset, channel_language, server_name, roles, member_role_on_join, include_voice, voice_count, extra_channels, announcements_readonly, rules_text, welcome_text, delete_existing |
| `images` | commands, schedule_enabled, schedule_channel, schedule_time, schedule_source, schedule_text |
| `moderation` | commands, log_channel, auto_timeout_warns, block_invites, block_links, bad_words, caps_filter, mention_limit |
| `welcome` | channel, message, image, image_title, color, auto_role, dm_enabled, dm_text, leave_enabled, leave_text |
| `levels` | xp_per_message, cooldown_seconds, levelup_message, levelup_channel, rank_card, rewards |
| `giveaway` | manager_role, button_label, default_minutes, ping_winners |
| `polls` | who, allow_change |
| `roles` | mode, panel_channel, panel_text, roles, verify_role |
| `reminders` | channel, messages, days, mention |
| `rules` | `rules[]` mit `{{answer.<id>}}` und optional `enabled_if`; `custom_commands` (Zeilen „befehl = Antwort“), `private_answers` |
