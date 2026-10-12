---
id: REQ-080
title: Rechtschreibungsprüfung im WYSIWYG-Editor
type: functional
priority: could
status: proposed
version: 0.1.0
use_case: UC-09
created: 2026-06-26
author: requirements-engineer
---

# REQ-080: Rechtschreibungsprüfung im WYSIWYG-Editor

## Beschreibung

Alle WYSIWYG-Editoren in OEA (Arc42-Kapitel, Entity-Beschreibungen, Freitext-Properties) unterstützen Rechtschreibungsprüfung. Die Basisfunktion nutzt den Browser-nativen Spellcheck (kein Implementierungsaufwand). Optional kann ein LanguageTool-Server für erweiterte Grammatik- und Stilprüfung konfiguriert werden.

## Akzeptanzkriterien

**AC1** (Browser-native Basis):
- TipTap-Editor-Element hat `spellcheck="true"` gesetzt
- Der Browser markiert falsch geschriebene Wörter mit roter Unterstreichung
- Rechtsklick → Browser-Korrekturvorschläge (Standardverhalten)

**AC2** (Sprache der Prüfung):
- Browser-Spellcheck folgt der Systemsprache des Browsers/Betriebssystems
- Wenn LanguageTool aktiv: Prüfsprache folgt der in REQ-079 gewählten UI-Sprache des Nutzers; explizit im Editor überschreibbar

**AC3** (LanguageTool konfigurierbar):
- Admin kann in den Instanz-Einstellungen `languageToolUrl` hinterlegen (z.B. `http://languagetool:8010`)
- Wenn konfiguriert: Editor sendet Text an LanguageTool-API und zeigt Fehler mit farblicher Unterstreichung an
- Klick auf unterstrichenes Wort → Popup mit Korrekturvorschlägen; Klick auf Vorschlag → ersetzt Text

**AC4** (Fehlertypen mit LanguageTool):
- Rechtschreibfehler: rote Unterstreichung
- Grammatikfehler: blaue Unterstreichung
- Stil-Hinweise: graue Unterstreichung (deaktivierbar per Nutzer-Einstellung)

**AC5** (Fallback bei Nicht-Erreichbarkeit):
- Wenn LanguageTool nicht erreichbar: kein Fehler in der UI; Browser-nativer Spellcheck bleibt aktiv
- Verbindungsfehler wird nicht prominent angezeigt (kein roter Banner); optional: kleines Indikator-Icon in der Toolbar

**AC6** (Offline / Air-gapped):
- Self-hosted LanguageTool (`languagetool/languagetool` Docker-Image, LGPL) konfigurierbar; konsistent mit REQ-075
- Kein externer Dienst-Aufruf wenn `languageToolUrl` nicht gesetzt

**AC7** (Deaktivierbar):
- Nutzer kann Rechtschreibprüfung im Editor über Toolbar-Button deaktivieren (Präferenz wird gespeichert)

## Technische Hinweise

- TipTap unterstützt `spellcheck`-Attribut auf dem Editor-Root-Element nativ
- LanguageTool-Integration: TipTap-Custom-Extension; sendet Text-Blöcke via `POST /v2/check` an LanguageTool-Server; Fehler werden als TipTap-Decorations eingeblendet
- LanguageTool Docker-Image: `languagetool/languagetool` (LGPL); Ports: 8010; kein API-Key für self-hosted
- Sprach-Codes folgen BCP 47 (`en-US`, `de-DE`, `fr-FR`); Mapping aus REQ-079 i18n-Sprachschlüssel → BCP-47-Code
- Performance: Text nur bei Idle-Pause (> 500ms seit letzter Eingabe) an LanguageTool senden, nicht bei jedem Keystroke
