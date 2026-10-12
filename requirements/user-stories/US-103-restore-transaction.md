# US-103: Wiederherstellung ist atomar und rollback-sicher

**ID**: US-103
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich sicher sein, dass eine Wiederherstellung entweder vollständig ausgeführt oder vollständig zurückgerollt wird, damit kein inkonsistenter Zustand ohne Audit-Snapshot entstehen kann.

## Bezug

**Use Case**: [UC-15](../use-cases/UC-15-entitaetsstand-wiederherstellen.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-103](../req/REQ-103-wiederherstellung-atomare-transaktion.md)

## Akzeptanzkriterien

**AC1** (Rollback bei Fehler):
- Wenn: ein technischer Fehler nach dem Snapshot-Schreiben auftritt
- Dann: ist die Entität unverändert; kein Snapshot ist in `entity_versions` vorhanden

**AC2** (Erfolgreiche Wiederherstellung):
- Wenn: die Wiederherstellung erfolgreich ist
- Dann: sind Snapshot, Entitäts-Update und Versionseintrag alle vorhanden und konsistent

## Definition of Done

- [ ] AC1–AC2 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
