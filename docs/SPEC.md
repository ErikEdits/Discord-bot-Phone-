# PocketBot – Spezifikation (v1)

> **PocketBot** ist eine Android-App, mit der jeder ohne Programmierkenntnisse einen eigenen
> Discord-Bot baut und ihn **direkt auf dem Handy im Hintergrund** laufen lässt, bis man ihn ausschaltet.
> Für größere Bots empfiehlt die App einen richtigen Hoster.

Stand: 2026-11-05 · Grundlage: 50 Fragen/Antworten (siehe [Anhang A](#anhang-a--antworten-aus-dem-fragebogen))

---

## 1. Überblick

| Bereich | Entscheidung |
|---|---|
| Plattform | Nur Android (vorerst) |
| Technik | Kotlin nativ, Jetpack Compose |
| Sprache der App | Deutsch + Englisch (umschaltbar, Standard = Systemsprache) |
| Design | Discord-ähnlich dunkel (Blurple-Akzente) |
| Bot-Laufort | Wählbar: **auf dem Handy** (Standard) oder **auf einem Server/Hoster** |
| Login | Nur „Mit Discord anmelden“ (OAuth2) über Eriks bestehenden Bot (`ErikEdits/discord-bot`) |
| Backend | Eriks bestehender Bot + neues Modul „App-API“, öffentlich über Cloudflare Worker + Quick Tunnel |
| Bot-Token | Standard: nur lokal (Android Keystore). Optional: Cloud, **Ende-zu-Ende verschlüsselt** |
| Anzahl Bots | Kostenlos unbegrenzt (lokal). Später Premium = Hosting auf Eriks Servern |
| Funktionen | Baukasten „WENN → FALLS → DANN“ zum Antippen + fertige Module + Marktplatz |
| Musik | **Keine** |
| Geld | Spenden-Knopf + kleines AdMob-Banner (Standard an, abschaltbar). Später Premium-Hosting |
| Veröffentlichung | Erst APK über GitHub Releases (automatisch gebaut), später Play Store |

### 1.1 Architektur

```
┌──────────────────────── Handy (PocketBot-App) ────────────────────────┐
│  UI (Compose)  ──  Baukasten/Editor  ──  Room-DB (Bots, Regeln, Variablen)
│        │                                                               │
│  BotRuntimeService (Foreground Service, Typ specialUse)                │
│        └── Engine (Kotlin/JVM, JDA) ── WebSocket ──► Discord Gateway   │
└────────┬───────────────────────────────────────────────────────────────┘
         │ HTTPS (nur Login, Cloud-Tresor, Marktplatz, Remote-Config)
         ▼
  https://pocketbot-gateway.<name>.workers.dev   (Cloudflare Worker, feste Adresse)
         │ leitet weiter an aktuelle Quick-Tunnel-Adresse
         ▼
  https://<zufall>.trycloudflare.com  ──►  Eriks Bot-Server: App-API (FastAPI, Port 8081)
                                           (getrennt vom Admin-Panel auf 8080!)
```

- Der Bot des Nutzers spricht **direkt mit Discord**. Eriks Server ist dafür nicht nötig.
  Wenn Eriks Server ausfällt, laufen alle Handy-Bots weiter. Nur Login, Cloud-Tresor und Marktplatz pausieren.
- Die **Engine** ist ein reines Kotlin/JVM-Modul ohne Android-Abhängigkeiten. Dadurch läuft derselbe Bot
  auch als `pocketbot-runner.jar` auf jedem Hoster (bot-hosting.net, VPS, Pterodactyl …).

### 1.2 Module im Repo (Gradle)

| Modul | Inhalt |
|---|---|
| `app` | Android-App (Compose UI, Service, Room, AdMob, Updater) |
| `engine` | Reines Kotlin/JVM: Regel-Engine, Baustein-Katalog, Module, JDA-Anbindung |
| `runner` | Kleines JVM-Programm: `java -jar pocketbot-runner.jar bot.pocketbot` für Hoster |
| `shared` | Datenmodelle (kotlinx.serialization): Bot-Config, Regeln, Export-Format, API-DTOs |

Die Bot-Engine liegt hinter einem Interface `BotEngine`, damit JDA bei Problemen auf Android
durch eine eigene schlanke Gateway-Implementierung (OkHttp-WebSocket) ersetzt werden kann.
**Allererster technischer Schritt:** JDA 5 + Core-Library-Desugaring auf Android 8–15 testen
(verbinden, Slash-Command, 24 h Dauerlauf).

---

## 2. Ablauf für den Nutzer

### 2.1 Erster Start
1. Sprachwahl (vorausgewählt = Systemsprache).
2. Kurze Intro (3 Karten: „Bot bauen“, „Läuft auf deinem Handy“, „Keine Programmierkenntnisse nötig“).
3. **Nutzungsbedingungen + Datenschutz**: Hinweis auf Discord-Mindestalter (13, in DE 16 für
   Einwilligung in Datenverarbeitung) + Haken „Ich habe die Nutzungsbedingungen gelesen“.
4. **Werbe-Einwilligung** (Google UMP/DSGVO-Dialog), danach Banner standardmäßig an.
5. **Mit Discord anmelden** (Pflicht, bevor man einen Bot erstellt).

### 2.2 Anmelden (Discord OAuth2 über Eriks Bot)
- Knopf „Mit Discord anmelden“ öffnet einen Chrome Custom Tab → `GET {API}/auth/login?state=…&code_challenge=…`
- Discord fragt nach der Berechtigung (Scope nur `identify`) → zurück zur App über `pocketbot://auth?…`
- App tauscht den Einmal-Code + PKCE-Verifier gegen ein Session-Token (`POST /v1/auth/exchange`).
- Session-Token wird in `EncryptedSharedPreferences` gespeichert (90 Tage gültig, verlängert sich bei Nutzung).
- Details: [BACKEND-INTEGRATION.md](BACKEND-INTEGRATION.md).

### 2.3 Bot erstellen (Assistent, Schritt für Schritt)

| # | Schritt | Details |
|---|---|---|
| 1 | **„Neuen Bot erstellen“** | Auf der Startseite (Bot-Liste). Mehrere Bots möglich. |
| 2 | **Tutorial (optional)** | Wischbare Karten **mit Screenshots**: Developer Portal öffnen → „New Application“ → Bot → „Reset Token“ → Token kopieren → Intents einschalten. Knopf „Developer Portal öffnen“ (`https://discord.com/developers/applications`). Screenshots liegen in der App (DE + EN). |
| 3 | **Token einfügen** | Einfügen-Feld mit „Aus Zwischenablage einfügen“-Knopf. Token wird nie angezeigt (••••), nur die letzten 4 Zeichen. |
| 4 | **Sofort prüfen** | `GET https://discord.com/api/v10/users/@me` mit `Authorization: Bot <token>` → Bot-Name + Avatar anzeigen. Fehler 401 → „Token ungültig, wurde er zurückgesetzt?“ |
| 5 | **Intents-Check** | `GET /applications/@me` → `flags` prüfen (Bits 12/13 Presence, 14/15 Server Members, 18/19 Message Content). Fehlt ein nötiger Intent → Karte mit Anleitung + Screenshot, „Erneut prüfen“. Welche Intents nötig sind, ergibt sich aus den gewählten Modulen/Regeln (z.B. Prefix-Commands → Message Content). |
| 6 | **Speicherort des Tokens** | „Nur auf diesem Handy (empfohlen)“ oder „Verschlüsselt in der Cloud (für mehrere Geräte)“. Cloud → PIN festlegen (siehe 6.2). |
| 7 | **Für welchen Zweck?** | „Eigener Server“ / „Mehrere Server“. Bei „Mehrere Server“, oder sobald der Bot **≥ 5 Server oder ≥ 500 Mitglieder** hat, kommt die **Hoster-Empfehlung** (siehe 2.4). Man darf trotzdem aufs Handy. |
| 8 | **Wofür ist der Bot? (optional, überspringbar)** | Mehrfachauswahl: Moderation & Sicherheit · Community & Spaß · Support & Tickets · Gaming/Streamer/Utility · Sonstiges. |
| 9 | **Vorlage vorschlagen** | Aus dem Zweck wird eine fertige Vorlage gebaut (Module + Beispiel-Commands). Alles danach frei änderbar. Alternativ „Leer starten“ oder „Vom Marktplatz“. |
| 10 | **Einladungslink** | Automatisch erzeugt: `https://discord.com/oauth2/authorize?client_id=<id>&scope=bot+applications.commands&permissions=<berechnet>`. Die Berechtigungen werden aus den aktiven Modulen berechnet. Knöpfe „Kopieren“, „Teilen“, „Öffnen“. |
| 11 | **Starten** | Bot startet sofort im Hintergrund. Hinweis auf Akku-Einstellungen (siehe 4.2), wenn noch nicht erledigt. |

### 2.4 Hoster-Empfehlung
Die Liste kommt über die Remote-Config des Backends (`GET /v1/config`), damit Erik sie ohne
App-Update ändern kann. Startinhalt:

1. **PocketBot Premium** (Eriks Hosting), Anzeige „Bald verfügbar“
2. **bot-hosting.net** (kostenlos, Discord-Bot-Hoster)
3. **Kostenlos:** Oracle Cloud Free Tier, Railway
4. **Günstige VPS:** Hetzner, Netcup (ab ca. 4 €/Monat)
5. **Pterodactyl-basierte Bot-Hoster** (allgemeiner Hinweis)

Jeder Eintrag: Name, Kurzbeschreibung, Preis-Hinweis, Link, Mini-Anleitung „So läuft dein PocketBot dort“
(`pocketbot-runner.jar` + exportierte `.pocketbot`-Datei hochladen, Startbefehl).

---

## 3. Der Baukasten (eigene Funktionen ohne Coden)

### 3.1 Prinzip: WENN → FALLS → DANN
Jede eigene Funktion („Regel“) besteht aus Karten, die **untereinander** angezeigt werden:

```
┌ WENN ─────────────────────────────┐
│ Slash-Command  /hallo             │
└───────────────────────────────────┘
┌ FALLS (optional, mehrere) ────────┐
│ Nutzer hat Rolle  @Mitglied       │
└───────────────────────────────────┘
┌ DANN (eine oder mehrere) ─────────┐
│ Antworten: "Hallo {user.mention}!"│
│ Variable  münzen (Nutzer)  +10    │
└───────────────────────────────────┘
┌ SONST (optional) ─────────────────┐
│ Antworten (nur für ihn sichtbar): │
│ "Du brauchst die Rolle Mitglied." │
└───────────────────────────────────┘
```

- Alles wird **angetippt und ausgewählt**: Kanäle, Rollen und Emojis über Auswahllisten.
  Die App lädt die Listen live vom Bot.
- Texte mit **Platzhaltern** (`{user}`, `{user.mention}`, `{server}`, `{channel}`, `{var.münzen}`,
  `{arg.grund}`, `{random:1-6}` …). Ein „{ }“-Knopf über der Tastatur fügt sie ein.
- Mehrere Aktionen laufen nacheinander. Es gibt einen Baustein „Warte X Sekunden“.
- Jede Regel hat Schalter an/aus, Cooldown (pro Nutzer/Server) und erlaubte Kanäle/Rollen.
- **Test-Knopf:** Regel simulieren mit Beispiel-Nutzer (zeigt Vorschau, ohne Discord).

### 3.2 Auslöser (WENN), Beispiele für den Startkatalog

| Gruppe | Auslöser |
|---|---|
| Commands | Slash-Command (mit Optionen: Text, Zahl, Nutzer, Rolle, Kanal, Ja/Nein, Auswahl) · Prefix-Command (`!befehl`) · Rechtsklick auf Nutzer · Rechtsklick auf Nachricht |
| Interaktionen | Button geklickt · Auswahlmenü gewählt · Formular (Modal) abgeschickt |
| Nachrichten | Nachricht gesendet (enthält / beginnt mit / Regex) · bearbeitet · gelöscht · Reaktion hinzugefügt/entfernt |
| Mitglieder | Beitritt · Verlassen · Rolle bekommen/verloren · Nickname geändert · Boost · Timeout · Bann |
| Voice | Voice-Kanal betreten/verlassen/gewechselt |
| Server | Kanal/Rolle erstellt/gelöscht · Bot zu Server eingeladen |
| Zeit | Jeden Tag um … · Alle X Minuten · Am Datum … (Countdown) |
| Extern | Twitch live · YouTube neues Video · TikTok neues Video · Reddit-Beitrag · RSS-Feed · Webseite geändert · GitHub Release/Commit · Modrinth/CurseForge Update |
| Variablen | Variable erreicht Wert (z.B. Level-Up) |

### 3.3 Bedingungen (FALLS)
Nutzer hat/hat nicht Rolle · ist Admin/Moderator · ist Bot · Kanal ist … · Text enthält/ist/Regex ·
Variable `=, ≠, <, >` Wert · Zufall (X %) · Uhrzeit/Wochentag · Konto älter als X Tage ·
Mitglied seit X Tagen · Cooldown abgelaufen · UND/ODER-Gruppen.

### 3.4 Aktionen (DANN)

| Gruppe | Aktionen |
|---|---|
| Antworten | Antworten (öffentlich/nur für ihn) · Nachricht in Kanal · DM senden · Embed senden (Editor mit Vorschau) · Bild senden · Buttons/Menüs anhängen · Formular öffnen · Nachricht bearbeiten/löschen/anpinnen · Reaktion hinzufügen · Thread erstellen |
| Moderation | Verwarnen · Timeout · Kicken · Bannen/Entbannen · Nachrichten löschen (X Stück) · Slowmode setzen · Kanal sperren/entsperren |
| Rollen | Rolle geben/nehmen/umschalten · Temporäre Rolle (für X Zeit) |
| Kanäle | Kanal erstellen/löschen/umbenennen · Berechtigungen setzen · Ticket-Kanal öffnen/schließen |
| Variablen | Setzen · Erhöhen/Verringern · Zurücksetzen · Rangliste anzeigen |
| Ablauf | Warten · Zufällige Aktion · Andere Regel ausführen · Stopp |
| Handy | **Push an mich senden** · In Log schreiben |
| Web | Webhook aufrufen (HTTP GET/POST) |

### 3.5 Variablen (einfach)
- Typen: Zahl, Text, Ja/Nein.
- Geltungsbereich: **pro Nutzer** (z.B. Münzen, XP), **pro Server** (z.B. Zähler), **global**.
- Beispiel: „Münzen von {user} +10“, „Wenn XP ≥ 100 → Level +1, XP = 0, Nachricht ‚Level-Up!‘“.
- Gespeichert in Room (Handy) bzw. SQLite (Runner). Export enthält optional die Werte.

### 3.6 Command-Arten
Alle sind möglich: **Slash-Commands**, **Prefix-Commands** (Prefix pro Bot einstellbar, braucht
Message Content Intent), **Buttons & Menüs**, **Rechtsklick-Menüs** (User/Message Context Menu).
Slash-Commands werden beim Speichern automatisch bei Discord registriert (global oder pro Server wählbar).

### 3.7 Wie wir auf „20.000+ Funktionen“ kommen und besser als andere sind
- **Kombinierbarkeit:** ~60 Auslöser × ~40 Bedingungen × ~80 Aktionen (Startkatalog) ergeben
  Hunderttausende sinnvolle Kombinationen. Jede neue Baustein-Version vervielfacht das.
- **Fertige Module** (siehe 3.8): sofort nutzbar, einfach einschalten.
- **Marktplatz** (siehe 5): Nutzer teilen Vorlagen und Commands. Das Angebot wächst von selbst.
- **Alleinstellung:** läuft kostenlos und ohne Server auf dem eigenen Handy, komplett ohne Code,
  Ende-zu-Ende-verschlüsselter Cloud-Tresor, offline-fähiger Editor, Export auf jeden Hoster.

### 3.8 Fertige Module (v1)

| Kategorie | Module |
|---|---|
| Moderation & Sicherheit | **Automod** (Spam, Links/Einladungen, Wortfilter, Caps, Massen-Erwähnungen) · **Warn/Kick/Ban/Timeout** mit Verlauf pro Nutzer · **Mod-Logs** · **Anti-Raid** (Masseneintritt → Server sperren, Alarm) · **Verifizierung** (Knopf oder Captcha vor Schreibrecht) |
| Community & Spaß | **Level + Rangliste** · **Economy** (Münzen, Daily, Shop mit Rollen) · **Willkommen/Abschied** (mit Bild) · **Reaction/Button Roles** · **Giveaways** · **Umfragen** · **Minispiele** (Würfeln, Münzwurf, Quiz, TicTacToe, Zählen) |
| Support | **Tickets** (Panel, Kategorien, Transkript, Schließen) · FAQ/Auto-Antworten |
| Gaming/Streamer/Utility | **Alerts:** Twitch, YouTube, TikTok, Reddit, RSS/Webseiten, GitHub, Modrinth, CurseForge · Erinnerungen · Countdown · Temp-Voice · Member-Counter |

Module sind intern selbst nur **vorgefertigte Regel-Pakete + Einstellungsseite**. Dieselbe Engine
führt beides aus. Neue Alert-Quellen kommen Schritt für Schritt dazu.

> **Hinweis Alerts auf dem Handy:** Externe Quellen werden per Polling abgefragt (Standard alle
> 5 Minuten, einstellbar). Das kostet etwas Akku und Daten und wird im Dashboard angezeigt.

---

## 4. Laufen im Hintergrund

### 4.1 Foreground Service
- `BotRuntimeService` mit `foregroundServiceType="specialUse"` (Android 14+; `dataSync` hat ab
  Android 15 ein 6-h-Limit und ist deshalb ungeeignet). Begründung für den Play Store:
  „Hält eine dauerhafte Verbindung zu Discord für den vom Nutzer gestarteten Bot.“
- **Dauerhafte Benachrichtigung** mit Status („3 Bots online · 2 h 14 min“) und **Stopp-Knopf**
  (alle stoppen bzw. pro Bot über Aufklappen).
- Ein Service für alle Bots des Handys. Jeder Bot hat eine eigene Engine-Instanz.
- **Mehrere Bots gleichzeitig** erlaubt. Ab dem 3. gleichzeitig laufenden Bot erscheint ein Akku-/Leistungshinweis.
- Bot läuft, **bis der Nutzer ihn ausschaltet**.

### 4.2 Akku-Optimierung
- Prüfung `isIgnoringBatteryOptimizations`. Wenn nicht erlaubt → Bitte mit Systemdialog.
- **Hersteller-Anleitung:** App erkennt Hersteller (Xiaomi/MIUI/HyperOS, Samsung, Huawei/Honor,
  Oppo/Realme/OnePlus, Vivo …) und zeigt genau die Schritte (Autostart, „Keine Einschränkung“,
  App sperren in Letzte Apps) mit Knopf zur passenden Einstellungsseite, wo möglich.

### 4.3 Stabilität
- **Auto-Reconnect:** bei Netzabbruch automatisch neu verbinden (Discord Resume, Backoff).
- **Jedes Netz:** WLAN, mobile Daten oder anderes, kein Netz-Zwang.
- **Auto-Start nach Handy-Neustart:** Bots, die vorher liefen, starten wieder (`BOOT_COMPLETED`).
- **Akku-Schutz:** unter **15 %** (einstellbar) pausiert der Bot automatisch, ab **30 %** (einstellbar)
  startet er **von selbst wieder**. Während der Pause bleibt der Service mit Hinweis „Pausiert – Akku 14 %“.
- **Ungültiger Token** (401 / Close-Code 4004) → Bot stoppt + Push.

### 4.4 Push-Benachrichtigungen (lokal, kein Firebase nötig)
- Bot abgestürzt / offline (Reconnect schlägt dauerhaft fehl)
- Ungültiger Token
- Bot wurde zu einem neuen Server eingeladen
- Eigene Aktion „Push an mich senden“ aus dem Baukasten

### 4.5 Dashboard pro Bot
- **Status, Laufzeit, Ping** (Gateway-Latenz)
- **Live-Logs** (Fehler rot, Filter, letzte 2000 Einträge)
- **Akku- und Datenverbrauch** (Datenverbrauch über `TrafficStats` pro UID, Akku geschätzt aus Laufzeit/Events)
- Server-Liste mit Mitgliederzahl (Grundlage für die Hoster-Empfehlung)

---

## 5. Marktplatz (v1) & Import/Export

- **Export/Import als Datei** (`.pocketbot`, JSON): ganzer Bot, einzelnes Modul oder einzelne Regel.
  **Enthält niemals den Token.** Teilen über Android-Share-Sheet.
- **Marktplatz** über Eriks Backend:
  - Jeder angemeldete Nutzer darf hochladen: Vorlage (ganzer Bot), Modul oder Command.
  - Suchen, Kategorien, Sortierung (Neu/Beliebt), Likes, Anzahl Installationen.
  - **Melden-Knopf.** Meldungen gehen an Erik (Discord-Kanal). Erik (OWNER_ID) kann Einträge in der
    App ausblenden/löschen.
  - Automatische Prüfung beim Hochladen: gültiges Schema, max. 256 KB, **kein Token-Muster** enthalten.
    Da Bausteine kein Code sind, kann eine Vorlage keinen Schadcode ausführen.
  - Vor dem Installieren: Vorschau aller Regeln + benötigte Berechtigungen/Intents.

---

## 6. Sicherheit & Datenschutz

### 6.1 Token lokal (Standard)
- Verschlüsselt mit AES-256-GCM. Der Schlüssel liegt im **Android Keystore** (nicht exportierbar).
- Token verlässt das Handy nur Richtung `discord.com`.

### 6.2 Token in der Cloud (optional, Ende-zu-Ende)
- Nutzer legt eine **Tresor-PIN/Passwort** fest (mind. 6 Zeichen).
- Schlüssel = **Argon2id**(PIN, zufälliges Salt), Verschlüsselung **AES-256-GCM** auf dem Handy.
- An den Server geht nur ein **undurchsichtiger Blob** (Version, KDF-Parameter, Salt, Nonce, Ciphertext).
  **Eriks Server kann den Token nicht lesen.**
- Neues Gerät: anmelden → PIN eingeben → Bots + Tokens wiederhergestellt.
- PIN vergessen → Cloud-Token unbrauchbar (Hinweis beim Einrichten!). Neuer Token aus Developer Portal nötig.
- Später **Premium-Hosting** braucht den Token auf Eriks Server: dafür eine **separate, ausdrückliche
  Freigabe** pro Bot (dann serverseitige Verschlüsselung). Nicht Teil von v1.

### 6.3 Keine Geheimnisse in der APK
- In der App steckt **nur die öffentliche Adresse** des Workers (`POCKETBOT_API_URL`). Das ist kein Geheimnis.
- **Cloudflare-API-Key, Gateway-Secret, Discord Client Secret** liegen ausschließlich auf Eriks Server
  in der `.env` (nie im Repo, nie in der App). Alles in einer APK kann ausgelesen werden, auch
  Werte aus GitHub-Secrets, die beim Bauen eingesetzt werden.
- GitHub-Secrets im App-Repo nur für: Signier-Keystore (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
  `KEY_ALIAS`, `KEY_PASSWORD`). Optional `POCKETBOT_API_URL` und `ADMOB_APP_ID` als Repo-Variablen
  (nicht geheim, aber bequem änderbar).

### 6.4 Konto löschen
- Einstellungen → „Konto und Cloud-Daten löschen“ → `DELETE /v1/me` (DSGVO).

---

## 7. Werbung, Spenden, Premium

- **AdMob-Banner**, klein (adaptive Banner, unten auf Bot-Liste und Dashboard). **Nie** im Editor
  und im Erstell-Assistenten.
- Standard **an**, in den Einstellungen per Schalter **abschaltbar**, ohne Bedingungen.
- DSGVO-Einwilligung über Google **UMP SDK** beim ersten Start.
- **Spenden-Knopf** (Ko-fi/PayPal-Link aus Remote-Config).
- **Premium (später):** Hosting der Bots auf Eriks Servern. Lokale Bots bleiben immer kostenlos und unbegrenzt.

---

## 8. Updates & Veröffentlichung

- **GitHub Actions** baut bei jedem Push eine Debug-APK (Artifact) und bei jedem Tag `v*` eine
  **signierte Release-APK** + `pocketbot-runner.jar` → GitHub Release.
- **In-App-Updater** (nur Build-Variante `github`): prüft `api.github.com/repos/ErikEdits/Discord-bot-Phone-/releases/latest`,
  zeigt Changelog, lädt APK herunter, installiert über `PackageInstaller`
  (`REQUEST_INSTALL_PACKAGES`). Die Play-Store-Variante `play` enthält keinen Updater.
- Remote-Config `min_app_version`: zu alte Versionen werden zum Update aufgefordert.

---

## 9. Technik-Stack (App)

| Thema | Wahl |
|---|---|
| Sprache/UI | Kotlin 2.x, Jetpack Compose, Material 3 mit eigenem Discord-Dark-Theme |
| Min/Target SDK | minSdk 26 (Android 8.0), targetSdk aktuell |
| Bot-Engine | JDA 5 (hinter Interface `BotEngine`), Core-Library-Desugaring |
| DB | Room (Bots, Regeln, Variablen, Logs) |
| Netzwerk | OkHttp + Retrofit/Ktor-Client, kotlinx.serialization |
| DI | Hilt |
| Krypto | Android Keystore, Tink oder libsodium (Argon2id) |
| Login | Chrome Custom Tabs + Deep Link `pocketbot://auth` |
| Werbung | Google Mobile Ads (AdMob) + UMP |
| Hintergrund | Foreground Service `specialUse`, WorkManager für Watchdog |
| Sprachen | `values/strings.xml` (EN), `values-de/strings.xml` (DE) |

Farben (Discord-ähnlich): Hintergrund `#1E1F22` / `#2B2D31` / `#313338`, Akzent Blurple `#5865F2`,
Grün `#23A55A`, Rot `#F23F43`, Gelb `#F0B232`, Text `#F2F3F5` / `#B5BAC1`.

---

## 10. Roadmap

| Phase | Inhalt |
|---|---|
| **0 – Planung** ✅ | Diese Spezifikation + [BACKEND-INTEGRATION.md](BACKEND-INTEGRATION.md) |
| **1 – Backend** | App-API in `ErikEdits/discord-bot` (anderer Claude-Chat), Worker + Tunnel |
| **2 – App-Grundgerüst** | Gradle-Projekt, Theme, Login, Bot-Liste, CI-Build, JDA-auf-Android-Test |
| **3 – Bot erstellen** | Assistent (Tutorial, Token, Prüfung, Intents, Einladungslink), Foreground Service, Akku-Schutz, Auto-Start |
| **4 – Baukasten** | Regel-Engine, Editor (Karten), Variablen, Slash/Prefix/Buttons/Context-Menüs |
| **5 – Module** | Moderation, Community, Tickets, Alerts |
| **6 – Cloud & Marktplatz** | E2E-Tresor, Import/Export, Marktplatz |
| **7 – Release** | AdMob, Spenden, Updater, Runner-JAR, Hoster-Anleitungen, v1.0 auf GitHub |
| **Später** | Play Store, Premium-Hosting, Fernsteuerung von Bots auf Hostern über die App, mehr Bausteine/Alerts |

---

## Anhang A – Antworten aus dem Fragebogen

| # | Frage | Antwort |
|---|---|---|
| 1 | Technologie | Kotlin nativ |
| 2 | Wo läuft der Bot | Wählbar: Handy oder Server |
| 3 | Plattformen | Erstmal nur Android |
| 4 | Sprache | Deutsch + Englisch |
| 5 | Login | Discord OAuth2 über den bestehenden Bot `ErikEdits/discord-bot`, ohne Extra-Server und ohne neuen Key; Integrationsanleitung als MD für einen anderen Claude-Chat |
| 6 | Backend | Eriks Bot (siehe 5) |
| 7 | Token-Speicher | Standard lokal; optional Cloud (verschlüsselt auf Eriks Bot-Server, mit ausdrücklicher Freigabe) |
| 8 | Bot-Limit | Kostenlos unbegrenzt lokal; später Premium = Hosting auf Eriks Servern |
| 9 | Öffentlich erreichbar | Über Cloudflare (API-Key nur auf Server) |
| 10 | Login/Panel | Neues, separates App-API-Modul für alle Discord-Nutzer; Admin-Panel bleibt unverändert |
| 11 | Bot-Engine | Eriks Bot nur für Login/Key-Speicher; Nutzer-Bots eigene Engine |
| 12 | Alte Android-App | Nein, komplett neu |
| 13 | Domain | Keine, kostenloser Cloudflare-Tunnel |
| 14 | Cloud-Verschlüsselung | Ende-zu-Ende |
| 15 | Token-Eingabe | Einfügen-Feld, sofort prüfen, Intents-Check, Einladungslink erzeugen |
| 16 | Feste Adresse | Cloudflare Worker (workers.dev) + Quick Tunnel |
| 17 | Tutorial | Schritt für Schritt mit Bildern |
| 18 | Hoster-Empfehlung ab | ~5 Servern / 500 Mitgliedern |
| 19 | Hoster | bot-hosting.net, eigenes Premium (später), Oracle Free/Railway, Hetzner/Netcup, Pterodactyl-Hoster |
| 20 | Zweck-Kategorien | Alle, alles frei zusammenstellbar, ohne Coden |
| 21 | Nach dem Zweck | Fertige Vorlage vorschlagen |
| 22 | Baukasten | WENN → FALLS → DANN zum Antippen + fertige Module |
| 23 | 20.000+ Funktionen | Kombinierbare Bausteine, besser als alle anderen |
| 24 | Editor-Look | Karten untereinander |
| 25 | Command-Arten | Alle: Slash, Prefix, Buttons & Menüs, Rechtsklick |
| 26 | Variablen | Ja, einfach |
| 27 | Musik | Keine |
| 28 | Moderation | Automod, Warn/Kick/Ban/Timeout + Logs, Anti-Raid, Verifizierung |
| 29 | Community | Level + Economy, Willkommen + Reaction Roles, Giveaways + Umfragen, Tickets + Minispiele |
| 30 | Alerts | Twitch/YouTube/TikTok, Reddit/RSS/Webseiten, GitHub/Modrinth/CurseForge; später mehr |
| 31 | Hintergrund | Dauerhafte Benachrichtigung mit Stopp-Knopf |
| 32 | Akku-Optimierung | Hersteller-Anleitung |
| 33 | Neustart/Netz | Auto-Start, Auto-Reconnect, Akku-Schutz (Neustart ab 30 %), jedes Netz |
| 34 | Mehrere Bots | Ja, Warnung ab 3 |
| 35 | Dashboard | Status/Laufzeit/Ping, Live-Logs, Akku/Daten |
| 36 | Push | Absturz/offline, ungültiger Token, neuer Server |
| 37 | Design | Discord-ähnlich dunkel |
| 38 | Name | PocketBot |
| 39 | Release | Erst APK auf GitHub, später Play Store |
| 40 | Geld | Spenden-Knopf + optionale, kleine Werbung |
| 41 | Marktplatz | Ja, plus Import/Export |
| 42 | Backup | Export/Import als Datei |
| 43 | Werbung | AdMob-Banner, Standard an, abschaltbar |
| 44 | Marktplatz wann | Version 1 |
| 45 | Marktplatz-Prüfung | Jeder darf hochladen, Melden-Knopf |
| 46 | Alter | Hinweis + Haken bei Nutzungsbedingungen |
| 47 | Zuerst | Spezifikation + Backend-MD |
| 48 | CI | APK automatisch über GitHub Actions |
| 49 | Updates | In-App-Hinweis + Download |
| 50 | Erfahrung | Etwas Erfahrung, normale Erklärungen |
