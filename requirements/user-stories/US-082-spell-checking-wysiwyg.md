---
id: US-082
title: Rechtschreibungsprüfung im WYSIWYG-Editor
use_case: UC-09
requirement: REQ-080
priority: could
status: proposed
story_points: 3
created: 2026-06-26
---

# US-082: Rechtschreibungsprüfung im WYSIWYG-Editor

**Als** Solution Architekt (Michael, SH-04)  
**möchte ich** beim Schreiben von Arc42-Kapitel-Texten eine Rechtschreibungsprüfung nutzen,  
**damit** Dokumentationsqualität ohne externe Tools sichergestellt wird.

## Akzeptanzkriterien

- [ ] TipTap-Editor hat `spellcheck="true"` → Browser markiert Fehler nativ (0 Zusatzaufwand)
- [ ] Admin kann optional `languageToolUrl` konfigurieren (self-hosted LanguageTool-Server)
- [ ] Wenn konfiguriert: Fehler erscheinen inline (Rechtschreibung rot, Grammatik blau, Stil grau); Klick → Korrekturvorschläge
- [ ] Prüfsprache folgt der UI-Sprache des Nutzers (REQ-079); im Editor überschreibbar
- [ ] Text wird nur nach Idle-Pause (> 500ms) an LanguageTool gesendet
- [ ] Wenn LanguageTool nicht erreichbar: Browser-Spellcheck bleibt aktiv, kein Fehler in der UI
- [ ] Nutzer kann Prüfung per Toolbar-Button deaktivieren

## Technische Hinweise

- TipTap Custom-Extension für LanguageTool (`POST /v2/check`)
- Docker-Image: `languagetool/languagetool` (LGPL, self-hostable, REQ-075-konform)
- BCP-47-Sprach-Codes aus REQ-079 i18n-Schlüssel ableiten
