# US-097: Dasselbe Objekt in mehreren Ordnern einordnen

**ID**: US-097
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich dasselbe Repository-Objekt gleichzeitig in mehreren Ordnern referenzieren können, damit ich Querverbindungen (z.B. Domäne und Initiative) ohne Datenduplizierung abbilden kann.

## Bezug

**Use Case**: [UC-13](../use-cases/UC-13-navigationsbaum-verwalten.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-097](../req/REQ-097-navigationsbaum-mehrfach-einordnung.md)

## Akzeptanzkriterien

**AC1** (Mehrfach-Einordnung):
- Wenn: ich "SAP ERP" in Ordner "Finance" und "Cloud-Migration" einhänge
- Dann: existieren zwei eigenständige TreeNodeItems; die Entität selbst ist unverändert

**AC2** (Unabhängige Entfernung):
- Wenn: ich das Item aus "Finance" entferne
- Dann: bleibt der Eintrag in "Cloud-Migration" gültig

## Definition of Done

- [ ] AC1–AC2 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
