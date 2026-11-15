# PocketBot – Plan v2: fertige Bot-Typen + Fragen + KI

Stand: 2026-11-15 · ersetzt die Abschnitte 2.3 (Schritte 8–9), 3 und 5 aus [SPEC.md](SPEC.md).
Alles andere (Login, Hintergrund-Dienst, Akku-Schutz, Cloud-Tresor, Hoster-Empfehlung, Design) bleibt.

## 1. Idee

Statt eines leeren Baukastens wählt man **fertige Bot-Typen**: Ticket-Bot, Server-Einrichtung,
Bilder-Bot, Moderation, Willkommen, Level, Giveaway, Umfragen, Rollen-Auswahl, Erinnerungen, Spaß
und viele mehr. Man kann einen Typ nehmen oder mehrere in **einem** Bot kombinieren.

1. **Beschreiben:** Der Nutzer schreibt frei, was er will („Ich will einen Ticket-Bot mit Support und
   Bewerbung und eine Begrüßung mit Bild“).
2. **KI versteht es:** Eine kostenlose KI (OpenRouter, über Eriks Server) macht daraus eine Antwort,
   die die App versteht: welche Bot-Typen, welche Antworten schon feststehen.
3. **Fragen:** Die App stellt alle restlichen Fragen (viele, aber nur passende), vorausgefüllt mit dem,
   was die KI erkannt hat. Bei jeder Frage kann man auch frei antworten, dann hilft wieder die KI.
4. **Fertig:** Die App baut den Bot daraus. Jeder Bot-Typ hat danach eine **eigene Steuerung** in der App
   (z.B. „Bild jetzt senden“, „Ticket schließen“, „Server jetzt einrichten“, „Giveaway starten“).

Der freie WENN→FALLS→DANN-Editor wird **entfernt**. Die Regel-Engine bleibt intern: einfache Bot-Typen
werden daraus gebaut, und alte Bots laufen damit weiter (siehe 6).

## 2. Katalog auf GitHub

Die Bot-Typen sind **Beschreibungen (JSON), kein Programmcode** (Google Play verbietet nachgeladenen
Code). Sie liegen in diesem Repo unter `catalog/` und werden geladen von
`https://raw.githubusercontent.com/ErikEdits/Discord-bot-Phone-/main/catalog/…`
Eine Kopie ist in der App eingebaut (offline / falls GitHub nicht erreichbar).
Neue Bot-Typen oder Fragen = JSON-Datei hinzufügen, **kein App-Update nötig** (solange sie vorhandene
Module nutzen).

```
catalog/index.json            Liste aller Bot-Typen (id, Version, Datei, Name, Emoji, Kategorie)
catalog/types/<id>.json       Ein Bot-Typ: Fragen + welches Modul mit welchen Einstellungen
```

Ein Bot-Typ besteht aus:
- `module`: welches eingebaute Modul ihn ausführt (`tickets`, `server_setup`, `images`, `moderation`,
  `welcome`, `levels`, `giveaway`, `polls`, `roles`, `reminders`, `rules` …)
- `questions`: Fragen mit `id`, Typ (`text`, `long_text`, `text_list`, `choice`, `multi_choice`, `bool`,
  `number`, `channel`, `role`, `color`, `emoji`, `time`), Text DE/EN, Hilfe, Standardwert, Optionen,
  Bedingung (`show_if`), damit nur passende Fragen kommen
- `settings`: wie aus den Antworten die Moduleinstellungen werden (`{{answer.x}}`)
- für das Modul `rules`: Regel-Vorlagen mit `{{answer.x}}`, so lassen sich viele einfache Bots
  (Spaß, Infos, Befehle) **nur per JSON** bauen

## 3. Module (in der App eingebaut, in Kotlin)

| Modul | Kann | Steuerung in der App |
|---|---|---|
| `tickets` | Panel mit Knöpfen pro Kategorie, privater Ticket-Kanal (Nutzer + Team-Rolle), Begrüßungstext, Schließen/Übernehmen, max. offene Tickets pro Nutzer, Transkript als Datei in Log-Kanal | offene Tickets sehen, schließen, Panel neu senden |
| `server_setup` | richtet einen (leeren) Server ein: Kategorien, Text-/Sprachkanäle, Rollen mit Farben und Rechten, Regeln-/Willkommenstext. Vorlagen: Gaming, Community, Streamer, Schule/Lernen, Firma, Roleplay. **Bots dürfen keine Server mehr erstellen**, nur einrichten | Vorschau, „Jetzt einrichten“, Fortschritt |
| `images` | Bilder vom Handy senden (sofort/geplant), Zeitplan aus einer Sammlung, Befehle aus freien Quellen (`/katze`, `/hund`, `/meme`), erzeugte Bilder (Willkommensbild, Rangkarte) | Bild senden (Galerie), Zeitplan, Quellen an/aus |
| `moderation` | `/kick` `/ban` `/timeout` `/warn` `/clear`, Automod (Einladungen, Links, Wortfilter, Caps, Spam), Mod-Log | letzte Aktionen, Verwarnungen |
| `welcome` | Begrüßung/Abschied (Text + Bild), Auto-Rolle, DM | Test senden |
| `levels` | XP, Level-Up, `/rank` (+ Karte), `/top`, Rollen-Belohnungen | Rangliste |
| `giveaway` | `/giveaway` mit Knopf, automatische Auslosung | starten, auslosen, beenden |
| `polls` | `/umfrage` mit Knöpfen, Ergebnis | – |
| `roles` | Rollen per Knopf / Verifizierung | Panel neu senden |
| `reminders` | Ankündigungen nach Zeitplan | Liste, jetzt senden |
| `rules` | generische Regeln (für JSON-only Bot-Typen und alte Bots) | – |

Alle Module zusammen bilden einen Bot (ein Token). Die App rechnet aus, welche Intents und Rechte
nötig sind, und zeigt den passenden Einladungslink und Hinweise.

## 4. KI über OpenRouter

- **Der OpenRouter-Key liegt nur auf Eriks Bot-Server** (`.env`), nie in der App.
- App → `POST /v1/ai/interpret` (Login nötig) → Server baut den Prompt aus dem Katalog, fragt ein
  **kostenloses** Modell (`:free`, Preis 0) und prüft die Antwort gegen den Katalog → App bekommt
  JSON: Bot-Typen + Antworten + kurze Zusammenfassung.
- Läuft **asynchron** (Job + Abfrage), weil kostenlose Modelle langsam sein können und das Relay
  nach 25 s abbricht.
- **Limit: 10 KI-Anfragen pro Nutzer und Tag.** Ist das Limit erreicht oder die KI offline, geht alles
  auch nur mit Fragen.
- **Datenschutz:** Beim ersten Mal Hinweis + Zustimmung („Dein Text wird an einen KI-Anbieter
  geschickt und kann dort gespeichert werden. Keine Passwörter/Tokens eingeben.“). Ohne Zustimmung
  nur Fragen. Der Server loggt keine Texte. Tokens im Text werden vor dem Senden entfernt.
- Details für den Server: [BACKEND-AI.md](BACKEND-AI.md).

## 5. Ablauf in der App

1. Neuer Bot → Token (wie bisher: Tutorial, Prüfung, Intents, Speicherort).
2. **„Was soll dein Bot können?“** Freitext (mit KI) **oder** „Selbst auswählen“ (Kacheln).
3. Übersicht: erkannte Bot-Typen (abwählbar, weitere hinzufügbar).
4. **Fragen** pro Bot-Typ, Seite für Seite, vorausgefüllt; Fortschrittsbalken; „Weiß nicht → Standard“.
5. Zusammenfassung + fehlende Intents/Rechte + Einladungslink → Start.
6. Dashboard: pro Bot-Typ eine Karte mit Steuerung; „Antworten ändern“ öffnet die Fragen wieder.

## 6. Alte Bots

Bots aus dem Baukasten werden **automatisch umgewandelt**: Ihre Regeln laufen unverändert als
„Eigene Funktionen (aus alter Version)“ im Modul `rules` weiter. In der App sieht man sie als Liste
(an/aus, löschen), aber man kann sie nicht mehr im Editor bearbeiten.

## 7. Reihenfolge

| Schritt | Inhalt |
|---|---|
| A | Katalog-Format + Lader (GitHub + eingebaute Kopie), Fragen-Ablauf, KI-Zustimmung, Editor entfernen, Umwandlung alter Bots |
| B | Grundgerüste **aller** Module + erste Bot-Typen im Katalog |
| C | KI-Anbindung (sobald der Server-Endpunkt läuft) |
| D | Steuerungen in der App pro Modul ausbauen, mehr Bot-Typen im Katalog |
