# US-098: Änderungshistorie einer Entität als Zeitlinie einsehen

**ID**: US-098
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich die vollständige Änderungshistorie einer Entität chronologisch einsehen und nach Zeitraum oder Person filtern können, damit ich Änderungen nachvollziehen und Compliance-Anforderungen erfüllen kann.

## Bezug

**Use Case**: [UC-14](../use-cases/UC-14-aenderungshistorie-einsehen.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-098](../req/REQ-098-versionshistorie-zeitlinie.md)

## Akzeptanzkriterien

**AC1** (Zeitlinie mit Versionen):
- Wenn: ich eine Entität mit 5 Versionen öffne
- Dann: sehe ich 5 Einträge in der Zeitlinie, neueste zuerst

**AC2** (Filter nach Actor):
- Wenn: ich die Zeitlinie nach Actor "Michael" filtere
- Dann: werden nur Versionen von Michael angezeigt

**AC3** (Entität ohne Historie):
- Wenn: ich eine Entität ohne Änderungshistorie öffne
- Dann: zeigt der Tab "Keine Änderungen seit Anlage. Erstellt am [Datum] von [Person]."

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
