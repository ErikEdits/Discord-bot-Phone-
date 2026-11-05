# PocketBot 📱🤖

Baue deinen eigenen Discord-Bot **ohne Programmieren**, direkt auf dem Handy. Er läuft im Hintergrund,
bis du ihn ausschaltest.

> Status: **Planung abgeschlossen**, als Nächstes kommt das Backend, danach das App-Grundgerüst.

- 📄 [Spezifikation](docs/SPEC.md): was die App kann und wie sie aufgebaut ist
- 🔌 [Backend-Integration](docs/BACKEND-INTEGRATION.md): Auftrag für den Einbau der App-API in
  [`ErikEdits/discord-bot`](https://github.com/ErikEdits/discord-bot) (an einen Claude-Code-Chat in jenem Repo geben)

## Kurz erklärt
- Android-App (Kotlin), Deutsch + Englisch, Discord-Look
- Anmelden mit Discord → Bot-Token eintragen (mit Bilder-Tutorial) → Zweck wählen → Vorlage → los
- Eigene Commands und Funktionen per **WENN → FALLS → DANN**-Bausteinen, dazu fertige Module
  (Moderation, Level, Economy, Tickets, Giveaways, Alerts …) und ein Marktplatz zum Teilen
- Bot-Token bleibt standardmäßig nur auf dem Handy; optional Ende-zu-Ende-verschlüsselt in der Cloud
- Für große Bots: Export auf einen Hoster (`pocketbot-runner.jar`)

## Lizenz
Siehe [LICENSE](LICENSE).
