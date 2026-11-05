# Auftrag: PocketBot-App-API in `ErikEdits/discord-bot` einbauen

> **Für Claude Code:** Diese Datei ist eine vollständige Arbeitsanweisung. Baue die beschriebene
> App-API in das bestehende Repo `ErikEdits/discord-bot` ein. Lies vorher `bot.py`, `web/app.py`
> (bestehender Discord-OAuth-Flow) und `cogs/mclog_cloudflare.py` (bestehende Cloudflare-API-Automatik,
> `cf_call`, `setup_mailbox`). Diese Muster sollen wiederverwendet werden.
> Antworte dem Nutzer (Erik) auf Deutsch, er hat etwas Programmiererfahrung.

## 0. Worum geht es?

**PocketBot** ist eine Android-App (Repo `ErikEdits/Discord-bot-Phone-`, Spezifikation: `docs/SPEC.md`),
mit der jeder einen eigenen Discord-Bot baut, der auf dem Handy läuft. Eriks bestehender Bot dient als
**Backend** für die App, ohne zusätzlichen Server und ohne neue Discord-Application:

1. **Login** für *jeden* Discord-Nutzer per OAuth2 über die **bestehende** Application des Bots
   (`DISCORD_CLIENT_ID`/`DISCORD_CLIENT_SECRET` existieren schon).
2. **Cloud-Tresor**: speichert Ende-zu-Ende-verschlüsselte Blobs (Bot-Tokens der Nutzer). Der Server
   kann sie **nicht** entschlüsseln.
3. **Marktplatz**: Nutzer teilen Bot-Vorlagen/Commands (JSON), mit Likes und Melden-Funktion.
4. **Remote-Config**: Hoster-Liste, Mindest-App-Version, Spenden-Link usw.

### Harte Regeln
- Das bestehende **Admin-Panel (`web/app.py`, Port 8080) bleibt unverändert** und **privat**.
  Die App-API ist eine **eigene FastAPI-Instanz** auf **eigenem Port (Standard 8081, nur 127.0.0.1)**.
  Nur dieser Port wird über den Tunnel veröffentlicht.
- App-Nutzer haben **keinerlei** Zugriff auf Panel, Bot-Server-Daten oder fremde Daten.
- Keine Geheimnisse ins Repo. Alles über `.env` (und `.env.example` um leere Einträge ergänzen).
- Kein neuer Discord-Key, keine neue Application.
- Bestehende Funktionen dürfen nicht kaputtgehen. Ist `POCKETBOT_ENABLED` nicht `true`, passiert gar nichts.

## 1. Architektur

```
Android-App ──HTTPS──► Cloudflare Worker "pocketbot-gateway"  (feste Adresse, kostenlos)
                        https://pocketbot-gateway.<subdomain>.workers.dev
                              │ fetch() an die aktuell registrierte Tunnel-Adresse
                              │ + Header X-PocketBot-Gateway-Key, X-PocketBot-Client-IP
                              ▼
                        https://<zufall>.trycloudflare.com      (cloudflared Quick Tunnel)
                              ▼
                        App-API (FastAPI) 127.0.0.1:8081 im Bot-Prozess
                              ▼
                        SQLite data/pocketbot.db
```

Warum so? Erik hat **keine eigene Domain**. Ein Quick Tunnel (`cloudflared tunnel --url …`) ist
kostenlos, bekommt aber bei jedem Start eine **neue zufällige** Adresse. Die App und die Discord-
Redirect-URI brauchen eine **feste** Adresse → der Worker auf `workers.dev` ist der feste Eingang
und merkt sich die aktuelle Tunnel-Adresse in einer **D1**-Mini-Tabelle (Entscheidung Erik, 2026-11-05: D1 statt KV,
damit der vorhandene Cloudflare-Token der MC-Logs reicht). Der Bot meldet sie nach jedem Tunnel-Start.
Später mit eigener Domain: benannter Tunnel, der Worker bleibt oder fällt weg, die App-URL bleibt gleich.

## 2. Neue Dateien / Änderungen

| Datei | Inhalt |
|---|---|
| `pocketbot/__init__.py` | Paket |
| `pocketbot/api.py` | Eigene `FastAPI`-App (`pocket_app`), alle Endpunkte aus Abschnitt 4 |
| `pocketbot/db.py` | SQLite-Zugriff (stdlib `sqlite3` über `asyncio.to_thread`, WAL-Modus), Schema-Migration |
| `pocketbot/auth.py` | OAuth-Flow, PKCE-Prüfung, Session-Tokens |
| `pocketbot/ratelimit.py` | Einfaches In-Memory-Rate-Limiting (Token-Bucket pro IP/Nutzer) |
| `pocketbot/validate.py` | Prüfung von Marktplatz-Inhalten (Schema, Größe, Token-Muster) |
| `pocketbot/gateway_worker.js` | Cloudflare-Worker-Quelltext (Abschnitt 5) |
| `cogs/pocketbot.py` | Cog: startet App-API-Server + cloudflared, registriert Tunnel-URL beim Worker, Watchdog, Admin-Slash-Commands, Meldungen in Discord |
| `bot.py` | Cog `cogs.pocketbot` in `EXTENSIONS` aufnehmen (beide Listen, falls zwei existieren) |
| `Dockerfile` | `COPY pocketbot/ ./pocketbot/` (cloudflared lädt der Cog selbst, siehe unten; der Live-Betrieb läuft ohne Docker auf bot-hosting.net) |
| `.env.example` | Neue Variablen (Abschnitt 3) |
| `requirements.txt` | Nichts Neues nötig (FastAPI, uvicorn, aiohttp sind da). Optional `argon2-cffi` **nicht** nötig: die Krypto macht die App. |
| `docs/POCKETBOT.md` | Kurze Betriebsanleitung für Erik (Einrichtung, Commands, Fehlerbehebung) |

> **Update 2026-11-05:** Der Bot läuft live auf **bot-hosting.net** (Pterodactyl, Python 3.14), also ohne Docker.
> Der Cog lädt `cloudflared` beim ersten Start selbst herunter (feste Version, Prüfsumme, Architektur erkennen,
> Ablage `data/bin/`), startet ihn mit `--protocol http2` und räumt verwaiste Prozesse per PID-Datei auf
> (Neustarts über `os.execv` rufen `cog_unload` nicht auf). Das Dockerfile-Beispiel unten ist nur für Docker-Betrieb.

Dockerfile-Beispiel für cloudflared (amd64; Version beim Umsetzen auf eine aktuelle feste Version setzen):
```dockerfile
ARG CLOUDFLARED_VERSION=2025.10.0
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
 && curl -fsSL -o /usr/local/bin/cloudflared \
    "https://github.com/cloudflare/cloudflared/releases/download/${CLOUDFLARED_VERSION}/cloudflared-linux-amd64" \
 && chmod +x /usr/local/bin/cloudflared && apt-get purge -y curl && rm -rf /var/lib/apt/lists/*
```
Läuft der Bot ohne Docker (Windows-PC, `start-bot.bat`), soll der Cog `cloudflared` im PATH oder unter
`POCKETBOT_CLOUDFLARED_PATH` suchen und sonst eine klare Fehlermeldung loggen (+ Hinweis in `/pocketbot status`).

## 3. Umgebungsvariablen (`.env`, nie committen)

| Variable | Beispiel | Bedeutung |
|---|---|---|
| `POCKETBOT_ENABLED` | `true` | Schaltet alles an. Standard `false`. |
| `POCKETBOT_PORT` | `8081` | Port der App-API (bindet immer an `127.0.0.1`) |
| `POCKETBOT_PUBLIC_URL` | `https://pocketbot-gateway.erik.workers.dev` | Feste Worker-Adresse. Wird bei `/pocketbot setup` automatisch gesetzt (in `data/pocketbot_gateway.json` gespeichert; die Variable überschreibt). |
| `POCKETBOT_GATEWAY_SECRET` | *(zufällig, 48 Zeichen)* | Gemeinsames Geheimnis Worker ↔ Bot. Wird bei Setup erzeugt und in `data/pocketbot_gateway.json` gespeichert, falls nicht gesetzt. |
| `CLOUDFLARE_API_TOKEN` | *(Token)* | Nur für das automatische Deployen des Workers. Rechte: `Account · Workers Scripts · Edit`, `Account · D1 · Edit`, `Account · Account Settings · Read`. **Der vorhandene mclog-Token (`data/settings.json` → `mclog_cloudflare`) hat genau diese Rechte und wird wiederverwendet.** Die Variable ist nur ein optionaler Override. |
| `CLOUDFLARE_ACCOUNT_ID` | *(optional)* | Sonst automatisch ermittelt |
| `POCKETBOT_REPORT_CHANNEL_ID` | `123…` | Kanal für Marktplatz-Meldungen und Status-Alarme (optional; sonst DM an `OWNER_ID`) |
| `POCKETBOT_CLOUDFLARED_PATH` | `cloudflared` | Pfad zum Binary |
| `DISCORD_CLIENT_ID` / `DISCORD_CLIENT_SECRET` / `OWNER_ID` | *(existieren schon)* | Werden wiederverwendet |

**Einmalig im Discord Developer Portal** (bestehende Application → OAuth2 → Redirects) hinzufügen:
`https://pocketbot-gateway.<subdomain>.workers.dev/auth/callback`
`/pocketbot setup` soll diese exakte URL am Ende anzeigen und darauf hinweisen.

## 4. API-Vertrag (verbindlich, die App wird genau dagegen gebaut)

Basis-URL = `POCKETBOT_PUBLIC_URL`. Alle JSON-Antworten UTF-8. Fehlerformat immer:
```json
{ "error": "snake_case_code", "message": "Lesbarer Text (Englisch)" }
```
Authentifizierte Endpunkte: Header `Authorization: Bearer <session_token>`. Fehlt er oder ist er ungültig → `401 {"error":"unauthorized"}`.

Jeder Request, der **nicht** den korrekten Header `X-PocketBot-Gateway-Key` (vom Worker gesetzt) trägt,
wird mit `403` abgelehnt (Ausnahme: Anfragen von `127.0.0.1` ohne Tunnel, für lokale Tests).
Die Client-IP für Rate-Limits kommt aus `X-PocketBot-Client-IP` (vom Worker aus `CF-Connecting-IP` gesetzt).

### 4.1 Allgemein
| Methode & Pfad | Auth | Antwort |
|---|---|---|
| `GET /v1/health` | – | `{"ok":true,"version":"1","time":<unix>}` |
| `GET /v1/config` | – | Remote-Config, siehe unten |

`/v1/config` liest `data/pocketbot_config.json` (wird mit Standardwerten angelegt, Erik kann sie bearbeiten
oder per `/pocketbot config` ändern):
```json
{
  "min_app_version": 1,
  "latest_app_version": 1,
  "ads_default_on": true,
  "donate_url": "https://ko-fi.com/…",
  "announcement": null,
  "hoster_recommend_threshold": { "guilds": 5, "members": 500 },
  "hosters": [
    { "id": "premium", "name": "PocketBot Premium", "status": "coming_soon", "url": null,
      "price": { "de": "Bald verfügbar", "en": "Coming soon" }, "desc": { "de": "…", "en": "…" } },
    { "id": "bot-hosting", "name": "bot-hosting.net", "url": "https://bot-hosting.net", "price": {"de":"Kostenlos","en":"Free"}, "desc": {"de":"…","en":"…"} },
    { "id": "oracle-free", "name": "Oracle Cloud Free Tier", "url": "https://www.oracle.com/cloud/free/", "price": {"de":"Kostenlos","en":"Free"}, "desc": {"de":"…","en":"…"} },
    { "id": "railway", "name": "Railway", "url": "https://railway.com", "price": {"de":"Kleines Gratis-Kontingent","en":"Small free tier"}, "desc": {"de":"…","en":"…"} },
    { "id": "hetzner", "name": "Hetzner Cloud", "url": "https://www.hetzner.com/cloud", "price": {"de":"ab ca. 4 €/Monat","en":"from ~4 €/month"}, "desc": {"de":"…","en":"…"} },
    { "id": "netcup", "name": "netcup", "url": "https://www.netcup.com", "price": {"de":"ab ca. 4 €/Monat","en":"from ~4 €/month"}, "desc": {"de":"…","en":"…"} },
    { "id": "pterodactyl", "name": "Pterodactyl-Bot-Hoster", "url": null, "price": {"de":"unterschiedlich","en":"varies"}, "desc": {"de":"…","en":"…"} }
  ]
}
```

### 4.2 Login (Discord OAuth2 + PKCE zwischen App und Backend)

Ablauf:
1. App erzeugt `state` (zufällig) und `code_verifier` (43–128 Zeichen), `code_challenge = BASE64URL(SHA256(code_verifier))`.
2. App öffnet im Custom Tab: `GET /auth/login?state=<state>&code_challenge=<challenge>`
3. Backend speichert `{state, code_challenge, created}` (10 min gültig) und leitet weiter zu
   `https://discord.com/oauth2/authorize?client_id=…&response_type=code&scope=identify&redirect_uri=<PUBLIC_URL>/auth/callback&state=<server_state>&prompt=none`
   (`server_state` ist ein eigener Zufallswert, der auf den App-`state` zeigt; Cookie nicht nötig).
4. `GET /auth/callback?code&state` → Code bei Discord tauschen (`/oauth2/token`, `redirect_uri` = genau die öffentliche URL!),
   `GET /users/@me` → Nutzer anlegen/aktualisieren → Einmal-`login_code` (zufällig, 2 min gültig, einmal nutzbar) erzeugen →
   `302` auf `pocketbot://auth?login_code=<code>&state=<App-state>`.
   Bei Fehler: `302` auf `pocketbot://auth?error=<code>&state=<App-state>`. Fehlercodes: `access_denied`, `oauth_failed`, `expired`.
   Zusätzlich eine kleine HTML-Seite als Fallback („Zurück zur App“-Link), falls der Browser den Redirect nicht ausführt.
5. App: `POST /v1/auth/exchange`
   ```json
   { "login_code": "…", "code_verifier": "…", "device_name": "Pixel 8" }
   ```
   → prüft `SHA256(code_verifier) == code_challenge` → Antwort:
   ```json
   { "session_token": "…", "expires_at": 1767225600,
     "user": { "id": "123", "username": "erik", "global_name": "Erik", "avatar_url": "https://cdn.discordapp.com/…" ,
               "is_owner": false, "premium": false } }
   ```

| Methode & Pfad | Auth | Beschreibung |
|---|---|---|
| `GET /auth/login` | – | Siehe oben |
| `GET /auth/callback` | – | Siehe oben |
| `POST /v1/auth/exchange` | – | Siehe oben |
| `GET /v1/me` | ✔ | `user`-Objekt wie oben |
| `POST /v1/auth/logout` | ✔ | Löscht diese Session → `{"ok":true}` |
| `GET /v1/sessions` | ✔ | Liste eigener Geräte `[{id, device_name, created_at, last_used_at, current}]` |
| `DELETE /v1/sessions/{id}` | ✔ | Gerät abmelden |
| `DELETE /v1/me` | ✔ | **Konto + alle Daten löschen** (Sessions, Tresor, eigene Marktplatz-Einträge, Likes, Meldungen des Nutzers) → `{"ok":true}` |

Session-Tokens: 32 Byte zufällig (`secrets.token_urlsafe(32)`), in der DB **nur SHA-256-Hash** speichern.
Gültig 90 Tage, `expires_at` verlängert sich bei Nutzung (max. 1× pro Tag schreiben). Max. 10 Sessions pro Nutzer (älteste fliegt).
`is_owner` = Discord-ID == `OWNER_ID`.

### 4.3 Cloud-Tresor (Ende-zu-Ende verschlüsselt, Server sieht nur Blobs)

| Methode & Pfad | Auth | Body / Antwort |
|---|---|---|
| `GET /v1/vault` | ✔ | `{"items":[{"bot_id":"<discord app id>","label":"Mein Bot","blob":"<base64>","updated_at":<unix>}]}` |
| `PUT /v1/vault/{bot_id}` | ✔ | Body `{"label":"…","blob":"<base64>"}` → `{"ok":true,"updated_at":…}` |
| `DELETE /v1/vault/{bot_id}` | ✔ | `{"ok":true}` |

- `bot_id`: nur Ziffern, 17–20 Stellen. `label` ≤ 64 Zeichen. `blob` Base64, dekodiert ≤ 8 KB.
- Max. 200 Einträge pro Nutzer.
- Der Server interpretiert den Blob **nicht** und loggt ihn **nie**. Format (nur zur Info, von der App definiert):
  `{"v":1,"kdf":"argon2id","m":65536,"t":3,"p":1,"salt":"…","nonce":"…","ct":"…"}`.

### 4.4 Marktplatz

Eintrag (`item`):
```json
{ "id": "mk_ab12cd", "kind": "template|module|command", "title": "Ticket-System Deluxe",
  "description": "…", "category": "moderation|community|support|gaming|utility|other",
  "tags": ["tickets"], "lang": "de",
  "author": { "id": "123", "name": "Erik", "avatar_url": "…" },
  "likes": 12, "liked_by_me": false, "installs": 40, "reports": 0,
  "created_at": 1767225600, "updated_at": 1767225600, "hidden": false,
  "required_intents": ["message_content"], "schema_version": 1 }
```

| Methode & Pfad | Auth | Beschreibung |
|---|---|---|
| `GET /v1/market?q=&kind=&category=&lang=&sort=new\|top\|installs&page=1` | optional | `{"items":[item…],"page":1,"has_more":true}` (20 pro Seite, ohne `content`). Ausgeblendete Einträge nur für Owner sichtbar. |
| `GET /v1/market/{id}` | optional | `item` + `"content": {…}` |
| `POST /v1/market` | ✔ | Body: `{kind,title,description,category,tags,lang,content}` → `item` |
| `PATCH /v1/market/{id}` | ✔ (Autor) | Teil-Update gleicher Felder |
| `DELETE /v1/market/{id}` | ✔ (Autor oder Owner) | `{"ok":true}` |
| `POST /v1/market/{id}/like` | ✔ | Umschalten → `{"liked":true,"likes":13}` |
| `POST /v1/market/{id}/install` | optional | Zähler +1 (max. 1× pro Nutzer/IP pro Tag) → `{"installs":41}` |
| `POST /v1/market/{id}/report` | ✔ | Body `{"reason":"spam\|offensive\|broken\|token_leak\|other","text":"…"}` → `{"ok":true}` |
| `GET /v1/admin/reports` | ✔ Owner | Offene Meldungen |
| `POST /v1/admin/market/{id}/hide` | ✔ Owner | Body `{"hidden":true}` |
| `POST /v1/admin/reports/{id}/resolve` | ✔ Owner | `{"ok":true}` |

Validierung (`pocketbot/validate.py`):
- `title` 3–60, `description` ≤ 2000, max. 5 `tags` à ≤ 24 Zeichen, gültige `kind`/`category`.
- `content`: JSON-Objekt, serialisiert ≤ 256 KB, Feld `schema_version` (int) muss vorhanden sein.
  Restliche Struktur wird nicht tief geprüft (die App validiert beim Import).
- **Token-Schutz:** Den gesamten serialisierten Inhalt auf Discord-Token-Muster prüfen
  (`[A-Za-z0-9_-]{23,28}\.[A-Za-z0-9_-]{6}\.[A-Za-z0-9_-]{27,}`) sowie auf Schlüssel namens
  `token`/`bot_token`/`secret` → `422 {"error":"contains_secret"}`.
- `required_intents` aus `content.required_intents` übernehmen (Liste bekannter Werte).
- Eine Meldung → Nachricht in `POCKETBOT_REPORT_CHANNEL_ID` (oder DM an Owner) mit Titel, Autor, Grund und
  Buttons **„Ausblenden“**, **„Löschen“**, **„Ignorieren“** (persistente Views, nur Owner/Admins dürfen klicken).
  Ab 5 offenen Meldungen wird ein Eintrag automatisch ausgeblendet, bis Erik entscheidet.

### 4.5 Rate-Limits (429 `{"error":"rate_limited","retry_after":<sek>}` + Header `Retry-After`)
| Bereich | Limit |
|---|---|
| `/auth/*` | 10 / Minute / IP |
| Lesende Endpunkte | 120 / Minute / IP |
| Schreibende Endpunkte | 30 / Minute / Nutzer |
| `POST /v1/market` | 10 / Tag / Nutzer |
| `POST …/report` | 20 / Tag / Nutzer |

## 5. Cloudflare Worker `pocketbot-gateway`

`pocketbot/gateway_worker.js` (ES-Module-Syntax), D1-Binding `DB` (Tabelle `origin(id INTEGER PRIMARY KEY CHECK(id=1), url TEXT, updated_at INTEGER)`), Secret-Binding `GATEWAY_SECRET`:

- `POST /_gateway/register` mit Header `Authorization: Bearer <GATEWAY_SECRET>`, Body `{"origin":"https://xyz.trycloudflare.com"}`
  → nur `https://*.trycloudflare.com` (oder später eigene Domain) akzeptieren → `INSERT OR REPLACE INTO origin(id,url,updated_at) VALUES(1,?,?)` → `{"ok":true}`.
- `GET /_gateway/status` (Bearer) → `{"origin":…, "updated_at":…}`.
- Alle anderen Pfade: `origin` aus D1 holen (pro Isolate 30 s im Speicher cachen), Request 1:1 weiterleiten
  (Methode, Pfad, Query, Body, Header außer `Host`), zusätzlich setzen:
  `X-PocketBot-Gateway-Key: <GATEWAY_SECRET>` und `X-PocketBot-Client-IP: <CF-Connecting-IP>`.
  Eingehende Header mit diesen Namen vorher **entfernen** (Spoofing-Schutz).
- Redirects (302 von `/auth/*`) **nicht** folgen, sondern unverändert an den Client geben (`redirect: "manual"`).
- Kein Origin oder Origin nicht erreichbar → `503 {"error":"backend_offline","message":"PocketBot server is offline, try again later."}`.
- Kein CORS nötig (nur die native App spricht mit der API).

Deploy-Automatik im Cog (Muster aus `cogs/mclog_cloudflare.py` → `cf_call`, `setup_mailbox` wiederverwenden):
1. Account-ID ermitteln → 2. D1-Datenbank `pocketbot_gateway` anlegen/finden + Tabelle `origin` anlegen → 3. Worker `pocketbot-gateway`
   hochladen (Multipart mit `metadata.json`: `main_module`, Bindings `d1` **DB** und `secret_text` **GATEWAY_SECRET**)
   → 4. workers.dev-Subdomain ermitteln/aktivieren → 5. Health-Check `GET <url>/_gateway/status`.
Ergebnis in `data/pocketbot_gateway.json` speichern (`url`, `account_id`, `d1_id`, `secret`), Datei nur lesbar für den Bot.

## 6. Cog `cogs/pocketbot.py`

Beim Laden (`cog_load`), nur wenn `POCKETBOT_ENABLED=true`:
1. DB öffnen/migrieren.
2. **App-API-Server starten:** zweiter `uvicorn.Server` (wie `_start_web_panel` in `bot.py`, `lifespan="off"`),
   `host="127.0.0.1"`, `port=POCKETBOT_PORT`, App = `pocketbot.api.pocket_app`.
3. **cloudflared starten:** `asyncio.create_subprocess_exec(cloudflared, "tunnel", "--no-autoupdate", "--url", f"http://127.0.0.1:{port}")`,
   stderr zeilenweise lesen, Regex `https://[a-z0-9-]+\.trycloudflare\.com` → Adresse merken →
   `POST <worker>/_gateway/register`. Ausgabe von cloudflared ins Log (Level DEBUG, außer Fehler).
4. **Watchdog** (alle 60 s): `GET <worker>/v1/health`. 3 Fehlschläge hintereinander → cloudflared neu starten
   (Backoff 5 s → 5 min). Prozess beendet → neu starten. Status-Änderungen (online/offline) als Meldung in den Report-Kanal.
5. Beim Entladen/Herunterfahren: cloudflared sauber beenden (`terminate`, nach 5 s `kill`), uvicorn stoppen.
6. Aufräum-Task (stündlich): abgelaufene `oauth_states`, `login_codes`, Sessions löschen.

Slash-Commands (nur Owner/Administratoren), Gruppe `/pocketbot`:
| Command | Funktion |
|---|---|
| `/pocketbot guide` | Schritt-für-Schritt: Cloudflare-Konto, API-Token mit den 3 Rechten, Redirect-URL im Developer Portal |
| `/pocketbot setup [cloudflare_token]` | Worker + D1 deployen (Token aus Argument oder `CLOUDFLARE_API_TOKEN`), Secret erzeugen, URL anzeigen, **Redirect-URL zum Kopieren** anzeigen. Antworten ephemeral. |
| `/pocketbot status` | Worker-URL, Tunnel-URL, online?, Nutzerzahl, Sessions aktiv, Tresor-Einträge, Marktplatz-Einträge, offene Meldungen |
| `/pocketbot restart-tunnel` | cloudflared neu starten |
| `/pocketbot config key value` | Remote-Config-Feld setzen (z.B. `min_app_version`, `donate_url`, `announcement`) |
| `/pocketbot on` / `off` | App-API vorübergehend sperren (`503 maintenance`) |

## 7. Datenbank-Schema (SQLite, `data/pocketbot.db`)

```sql
CREATE TABLE users(id TEXT PRIMARY KEY, username TEXT, global_name TEXT, avatar TEXT,
                   created_at INTEGER, last_seen_at INTEGER, premium INTEGER DEFAULT 0, banned INTEGER DEFAULT 0);
CREATE TABLE sessions(id TEXT PRIMARY KEY, user_id TEXT REFERENCES users(id) ON DELETE CASCADE,
                      token_hash TEXT UNIQUE, device_name TEXT, created_at INTEGER, last_used_at INTEGER, expires_at INTEGER);
CREATE TABLE oauth_states(server_state TEXT PRIMARY KEY, app_state TEXT, code_challenge TEXT, created_at INTEGER);
CREATE TABLE login_codes(code_hash TEXT PRIMARY KEY, user_id TEXT, code_challenge TEXT, created_at INTEGER, used INTEGER DEFAULT 0);
CREATE TABLE vault(user_id TEXT REFERENCES users(id) ON DELETE CASCADE, bot_id TEXT, label TEXT, blob BLOB,
                   updated_at INTEGER, PRIMARY KEY(user_id, bot_id));
CREATE TABLE market_items(id TEXT PRIMARY KEY, author_id TEXT REFERENCES users(id) ON DELETE CASCADE, kind TEXT, title TEXT,
                   description TEXT, category TEXT, tags TEXT, lang TEXT, content TEXT, required_intents TEXT,
                   schema_version INTEGER, likes INTEGER DEFAULT 0, installs INTEGER DEFAULT 0,
                   hidden INTEGER DEFAULT 0, created_at INTEGER, updated_at INTEGER);
CREATE TABLE market_likes(item_id TEXT REFERENCES market_items(id) ON DELETE CASCADE, user_id TEXT, PRIMARY KEY(item_id, user_id));
CREATE TABLE market_installs(item_id TEXT, who TEXT, day INTEGER, PRIMARY KEY(item_id, who, day));
CREATE TABLE market_reports(id TEXT PRIMARY KEY, item_id TEXT REFERENCES market_items(id) ON DELETE CASCADE, user_id TEXT,
                   reason TEXT, text TEXT, created_at INTEGER, resolved INTEGER DEFAULT 0);
CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);  -- schema_version
```
`PRAGMA foreign_keys=ON; PRAGMA journal_mode=WAL;`. Suche: `LIKE` auf title/description/tags reicht für v1
(FTS5 optional). `data/` ist bereits ein persistentes Volume, es ist keine weitere Konfiguration nötig. Der bestehende Backup-Cog
soll `pocketbot.db` mit sichern, falls er Dateien aus `data/` sichert (prüfen; Tresor-Blobs sind ohnehin E2E-verschlüsselt).

## 8. Sicherheit (Checkliste)
- [ ] App-API bindet nur an `127.0.0.1`. Das Admin-Panel ist über den Tunnel **nicht** erreichbar (Test!).
- [ ] Ohne gültigen `X-PocketBot-Gateway-Key` → 403 (Vergleich mit `secrets.compare_digest`).
- [ ] Session-Tokens und Login-Codes nur gehasht speichern. Tresor-Blobs, Tokens, Codes nie loggen.
- [ ] Jeder Datenzugriff filtert nach `user_id` aus der Session (kein IDOR).
- [ ] `redirect_uri` bei Discord immer exakt `POCKETBOT_PUBLIC_URL + "/auth/callback"`.
- [ ] Request-Body-Limit (z.B. 300 KB) über Middleware.
- [ ] `docs_url=None, redoc_url=None, openapi_url=None` auf `pocket_app`.
- [ ] Gesperrte Nutzer (`banned=1`) → 403 `{"error":"banned"}`.

## 9. Tests
Neue Tests (Stil wie vorhandene Tests im Repo; falls keine vorhanden: `pytest` + `httpx`/`fastapi.testclient` als Dev-Abhängigkeit):
- OAuth: Discord-Aufrufe mocken; kompletter Flow login → callback → exchange; falscher Verifier → 400; Code zweimal → 400; abgelaufen → 400.
- Gateway-Key fehlt/falsch → 403.
- Tresor: CRUD, fremder Nutzer sieht nichts, Größenlimit.
- Marktplatz: Upload, Token-Muster → 422, Like-Toggle, Report → Auto-Hide ab 5, Owner-Rechte.
- `DELETE /v1/me` entfernt alles.
- Rate-Limit greift.
- Worker: zumindest Syntax-Check (`node --check`), wenn Node verfügbar.

## 10. Abnahme (Erik prüft)
1. `.env`: `POCKETBOT_ENABLED=true`, `CLOUDFLARE_API_TOKEN=…` → Bot neu starten.
2. In Discord `/pocketbot setup` → zeigt Worker-URL + Redirect-URL.
3. Redirect-URL im Developer Portal eintragen.
4. Im Browser `https://pocketbot-gateway.<subdomain>.workers.dev/v1/health` → `{"ok":true,…}`.
5. `…/auth/login?state=test&code_challenge=…` → Discord-Login erscheint → leitet auf `pocketbot://auth?login_code=…` (im PC-Browser ist das ein Fehler, das ist normal).
6. Bot neu starten → neue Tunnel-Adresse wird automatisch registriert, `/v1/health` funktioniert weiter.
7. `/pocketbot status` zeigt alles grün.
8. Die Worker-URL an PocketBot (App-Repo, Variable `POCKETBOT_API_URL`) geben.

Am Ende: kurze Zusammenfassung für Erik auf Deutsch + die Worker-URL. Committen und pushen nach den Regeln des Repos.
