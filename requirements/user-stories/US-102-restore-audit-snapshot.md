# US-102: Automatischer Audit-Snapshot vor Wiederherstellung

**ID**: US-102
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich, dass vor jeder Wiederherstellung automatisch ein Snapshot des aktuellen Zustands gesichert wird und dieser in der Zeitlinie als Wiederherstellungs-Eintrag sichtbar ist, damit der Audit-Trail lückenlos bleibt.

## Bezug

**Use Case**: [UC-15](../use-cases/UC-15-entitaetsstand-wiederherstellen.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-102](../req/REQ-102-wiederherstellung-audit-snapshot.md)

## Akzeptanzkriterien

**AC1** (Snapshot-Eintrag sichtbar):
- Wenn: eine Wiederherstellung ausgeführt wurde
- Dann: ist ein neuer Zeitlinie-Eintrag mit `restoredFromVersion=N` sichtbar

**AC2** (Markierung in Zeitlinie):
- Wenn: ich die Zeitlinie nach einer Wiederherstellung ansehe
- Dann: zeigt der Eintrag "Wiederhergestellt aus vN"

**AC3** (Snapshot vor Wiederherstellung erhalten):
- Wenn: die Wiederherstellung abgeschlossen ist
- Dann: ist der Zustand vor der Wiederherstellung als eigenständiger Snapshot vorhanden

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
