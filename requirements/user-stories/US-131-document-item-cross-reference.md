# US-131: Kapitel per {{ im WYSIWYG-Editor referenzieren

**ID**: US-131
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Michael – Solution Architekt möchte ich in einem Kapitel-Inhalt per `{{` auf ein anderes Kapitel derselben DocumentCollection verweisen können, damit Leser direkt zum referenzierten Abschnitt navigieren können und der Verweis automatisch korrekt bleibt, wenn ich das Zielkapitel umbenenne.

## Bezug

**Use Case**: [UC-09](../use-cases/UC-09-loesungsarchitektur-arc42-dokumentieren.md)
**Persona**: Michael – Solution Architekt (SH-04)
**Requirements**: [REQ-134](../req/REQ-134-document-item-querverweis.md)

## Akzeptanzkriterien

**AC1** (Autocomplete-Trigger):
- Wenn: Michael `{{` im Freitext-Bereich tippt
- Dann: Dropdown mit allen Items der aktuellen Collection öffnet sich; Suche über Alias und Name möglich

**AC2** (Einfügen und Rendering):
- Wenn: Michael ein Item aus dem Dropdown wählt
- Dann: Anklickbarer Inline-Link mit dem `name` des Ziel-Items erscheint im Text

**AC3** (Automatische Aktualisierung bei Umbenennung):
- Wenn: Michael das Ziel-Kapitel umbenennt
- Dann: Alle Querverweise auf dieses Kapitel zeigen automatisch den neuen Namen

**AC4** (Gelöschtes Ziel):
- Wenn: Das referenzierte Kapitel gelöscht wird
- Dann: Verweis zeigt `[gelöschtes Kapitel]` in roter Schrift; kein defekter Link

## Definition of Done

- [ ] AC1–AC4 erfüllt
- [ ] Tests vorhanden (inkl. Umbenennung und Löschen des Ziels)
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
