# US-101: Entität auf früheren Stand vollständig wiederherstellen

**ID**: US-101
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich eine Entität auf einen beliebigen früheren Stand zurücksetzen können und dabei einen Bestätigungsdialog mit Diff sehen, damit ich fehlerhafte Änderungen sicher und nachvollziehbar rückgängig machen kann.

## Bezug

**Use Case**: [UC-15](../use-cases/UC-15-entitaetsstand-wiederherstellen.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-101](../req/REQ-101-entitaet-vollwiederherstellung.md)

## Akzeptanzkriterien

**AC1** (Wiederherstellung auf früheren Stand):
- Wenn: ich eine Entität auf Version v3 wiederherstelle
- Dann: sind `name`, `description` und `properties` auf v3-Werte gesetzt; `id` ist unverändert

**AC2** (Bestätigungsdialog mit Diff):
- Wenn: ich die Wiederherstellung initiiere
- Dann: zeigt der Dialog, welche Felder sich zwischen aktuellem Stand und Zielversion unterscheiden

**AC3** (Abbruch ohne Änderung):
- Wenn: ich den Dialog abbreche
- Dann: bleibt die Entität unverändert

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
