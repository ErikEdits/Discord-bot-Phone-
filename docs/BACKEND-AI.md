# Auftrag: KI-Endpunkt (OpenRouter) für PocketBot in `ErikEdits/discord-bot`

> **Für Claude Code (BOT-Session):** Erweitere die bestehende PocketBot-App-API (`pocketbot/`) um
> einen KI-Endpunkt. Hintergrund: [SPEC-v2.md](SPEC-v2.md) Abschnitt 4. Antworten an Erik auf Deutsch.

## Zweck
Nutzer beschreiben in der App frei, was ihr Discord-Bot können soll. Eine **kostenlose** KI bei
OpenRouter macht daraus strukturiertes JSON: welche Bot-Typen aus dem Katalog und welche Antworten
auf deren Fragen. Der Katalog liegt öffentlich auf GitHub.

## Konfiguration (`.env`, nie committen)
| Variable | Standard | Bedeutung |
|---|---|---|
| `OPENROUTER_API_KEY` | – | Pflicht. Ohne Key → `503 ai_unavailable` |
| `POCKETBOT_AI_DAILY_LIMIT` | `10` | Anfragen pro Nutzer und Tag (UTC) |
| `POCKETBOT_AI_MODELS` | leer | Optional: Komma-Liste bevorzugter Modelle. Nur Modelle verwenden, die laut `GET https://openrouter.ai/api/v1/models` **kostenlos** sind (`pricing.prompt == "0"` und `pricing.completion == "0"`, i.d.R. Suffix `:free`). Ist die Liste leer oder sind alle nicht verfügbar: automatisch ein kostenloses Modell mit ausreichendem Kontext wählen (Modell-Liste 6 h cachen). Nie ein kostenpflichtiges Modell nutzen. |
| `POCKETBOT_CATALOG_URL` | `https://raw.githubusercontent.com/ErikEdits/Discord-bot-Phone-/main/catalog/` | Basis-URL des Katalogs |

## Katalog
`<CATALOG_URL>index.json` → `{ "version": 1, "types": [ { "id", "version", "file", "name": {de,en}, "description": {de,en}, "emoji", "category" } ] }`
`<CATALOG_URL><file>` → Bot-Typ mit `questions: [ { "id", "type", "label": {de,en}, "help": {de,en}, "options": [ { "value", "label": {de,en} } ], "default", "min", "max" } ]`
Fragetypen: `text`, `long_text`, `text_list`, `choice`, `multi_choice`, `bool`, `number`, `channel`, `role`, `color`, `emoji`, `time`.
`channel`/`role` kann die KI nicht wissen → immer weglassen. 10 Minuten cachen; Fehler → letzter Stand.

## API (Format wie der bestehende Vertrag: Fehler immer `{"error","message"}`, Auth per Bearer)

### `POST /v1/ai/interpret` → `202`
```json
{ "mode": "setup", "text": "Ich will einen Ticket-Bot …", "lang": "de",
  "type_ids": ["tickets"], "question_id": null }
```
- `mode: "setup"`: ganze Beschreibung → Bot-Typen + Antworten. `type_ids` optional (schon gewählte Typen).
- `mode: "question"`: Freitext-Antwort auf **eine** Frage: `type_ids` = genau ein Typ, `question_id` gesetzt.
- `text` 1–2000 Zeichen (sonst `413 text_too_long`). Vor dem Senden Discord-Token-Muster entfernen.
- Limit überschritten → `429 {"error":"ai_limit","retry_after":<s bis Mitternacht UTC>}`.
- Antwort: `{"job_id":"ai_…","remaining_today":9}`.

### `GET /v1/ai/jobs/{job_id}` (nur eigener Job)
- `{"status":"pending"}`
- `{"status":"done","result":{ "types":["tickets","welcome"], "answers":{"tickets":{"categories":["Support","Bewerbung"],"max_open":1}}, "summary":"Ticket-Bot mit 2 Kategorien …", "model":"…:free" }}`
- `{"status":"failed","error":"ai_unavailable|ai_bad_output|ai_timeout"}`
- Jobs 10 min aufheben, dann `404 not_found`.

## Prompt & Prüfung
- System-Prompt (Englisch, Antwortsprache = `lang`): „Du bist ein Assistent der eine Discord-Bot-Beschreibung
  in JSON übersetzt. Nutze NUR diese Bot-Typen und Fragen-IDs …“ + kompakte Katalog-Liste (id, Name, Fragen mit id/typ/optionen).
  Ausgabe **nur** JSON im Format von `result` (ohne `model`). Nicht verstandene Fragen weglassen.
- `response_format: {"type":"json_object"}` mitsenden, falls das Modell es unterstützt; sonst erstes JSON-Objekt aus dem Text parsen.
- Header `HTTP-Referer: https://github.com/ErikEdits/Discord-bot-Phone-` und `X-Title: PocketBot`.
- Timeout pro Modell 40 s, bei Fehler/429 nächstes kostenloses Modell (max. 3 Versuche).
- **Ergebnis gegen den Katalog prüfen:** unbekannte Typen/Fragen raus; `choice` nur erlaubte Werte; `number` in min/max;
  `bool` echt boolesch; `text_list` max. 25 Einträge à 100 Zeichen; Texte max. 1000 Zeichen.
  Ergebnis leer → `failed: ai_bad_output`.
- **Keine Nutzertexte loggen.** Nur Zähler (Anfragen, Fehler, Modell).
- Läuft im Hintergrund (asyncio-Task), damit das Relay (25 s) nicht abbricht.

## `/pocketbot status` / config
- Status: KI an/aus, aktuelles Modell, Anfragen heute, Fehler heute.
- `/pocketbot config ai_daily_limit <n>`, `/pocketbot config ai_models <liste>`.

## Tests
Mock für OpenRouter (Modelle-Liste + Chat): Erfolg, kaputtes JSON, unbekannte IDs werden gefiltert,
Limit 429, Modell-Fallback, nur kostenlose Modelle, Job-Isolation pro Nutzer.
