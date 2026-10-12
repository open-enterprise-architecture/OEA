---
id: REQ-134
title: DocumentItem-Querverweis via {{ im WYSIWYG-Editor
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-09
  business_objects:
    - document-collection
  stakeholders:
    - SH-03
    - SH-04
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-134: DocumentItem-Querverweis via {{ im WYSIWYG-Editor

## Aussage

Das System MUSS im WYSIWYG-Editor von DocumentItems das Tippen von `{{` als Auslöser für ein Autocomplete-Dropdown erkennen, das DocumentItems der **aktuellen DocumentCollection** durchsucht. Gesucht wird sowohl über `alias` als auch über `name`. Nach Auswahl wird ein ID-stabiler Querverweis eingefügt.

**Speicherformat (Freitext):** `{{Kapitelname|item:uuid}}`

**Rendering:** Das System löst beim Darstellen den aktuellen `name` des referenzierten DocumentItems via UUID auf und zeigt ihn als anklickbaren Inline-Link. Wird das Ziel-Item umbenannt, aktualisiert sich die Anzeige automatisch. Wird das Ziel-Item gelöscht, zeigt der Verweis `[gelöschtes Kapitel|item:uuid]` in roter Schrift.

**Scope:** Querverweise sind auf Items derselben `DocumentCollection` begrenzt (BR-15). Cross-Collection-Verweise sind nicht unterstützt.

**In Code-Blöcken** (Mermaid, PlantUML, Draw.io): `{{` löst ebenfalls Autocomplete aus; gespeichert wird der reine `name`-Text ohne ID-Klammerung; keine automatische Auflösung bei Umbenennung.

## Begründung

Ohne Querverweise müssen Autoren Kapitelnamen manuell tippen — bei Umbenennungen entstehen verwaiste Textreferenzen. Der `{{`-Mechanismus (analog zu `[[` für Entities) stellt sicher, dass Kapitelverweise konsistent bleiben, auch wenn die Dokumentationsstruktur reorganisiert wird.

## Akzeptanzkriterien

**AC1** (Autocomplete-Trigger):
- Wenn: Nutzer im Freitext-Bereich `{{` tippt
- Dann: Dropdown öffnet sich mit allen DocumentItems der aktuellen Collection; Suche über `alias` und `name`; erstes Ergebnis vorselektiert

**AC2** (Einfügen):
- Wenn: Nutzer einen DocumentItem aus dem Dropdown auswählt
- Dann: `{{Kapitelname|item:uuid}}` wird an Cursor-Position eingefügt; Dropdown schließt sich

**AC3** (Rendering — bestehender Name):
- Wenn: DocumentItem mit Querverweis gerendert wird und das Ziel existiert
- Dann: Anklickbarer Inline-Link mit aktuellem `name` des Ziel-Items dargestellt; Klick navigiert zum Ziel-Kapitel

**AC4** (Rendering — umbenanntes Ziel):
- Wenn: Ziel-Item umbenannt wird
- Dann: Alle Querverweise auf dieses Item zeigen automatisch den neuen Namen (ID-Auflösung)

**AC5** (Rendering — gelöschtes Ziel):
- Wenn: Ziel-Item gelöscht wird
- Dann: Verweis zeigt `[gelöschtes Kapitel|item:uuid]` in roter Schrift; keine Navigation möglich

**AC6** (Code-Block):
- Wenn: Nutzer in einem Mermaid- oder PlantUML-Block `{{` tippt und auswählt
- Dann: Gespeichert wird reiner `name`-Text (kein `|item:uuid`); kein Link im Rendering

## Abhängigkeiten

- **Voraussetzungen**: REQ-132 (DocumentItem mit `alias`), REQ-070 (Entity-Mention-Mechanismus als Vorlage)
- **Folgewirkungen**: –

## Realisierungs-Hinweise

- Implementierung analog zu REQ-070 (`[[`-Trigger für Entities): gleicher Editor-Extension-Mechanismus (Tiptap/ProseMirror), anderer Trigger-Character und andere Datenquelle
- Autocomplete-Quelle: `GET /api/v1/collections/{collectionId}/items?search=<query>` — gibt `id`, `name`, `alias` zurück
- Rendering: Custom Node in Tiptap; `name` wird bei jedem Render via API aufgelöst (gecacht, TTL 60 s)

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
