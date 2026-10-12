# US-119: Standards-Drift in der Ist-Landschaft analysieren

**ID**: US-119
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich alle Entitäten sehen, die einen verbotenen, veralteten oder vom TRM-Standard abweichenden SBB verwenden, damit ich gezielte Migrations-Maßnahmen planen kann.

## Bezug

**Use Case**: [UC-20](../use-cases/UC-20-continuum-conformance-analysieren.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-119](../req/REQ-119-standards-drift-analyse.md)

## Akzeptanzkriterien

**AC1** (prohibited-Entitäten):
- Wenn: 3 Entitäten `instanceOfSBBId=prohibited-SBB-UUID` haben
- Dann: sind alle 3 in der Drift-Liste; `prohibited` ist rot hervorgehoben

**AC2** (Abweichungs-Report):
- Wenn: eine Entität einen SBB verwendet, der nicht dem `preferredStandard` ihrer TRM-Kategorie entspricht
- Dann: erscheint sie in der Abweichungs-Liste

**AC3** (Direkt-Link):
- Wenn: ich einen Eintrag in der Drift-Liste anklicke
- Dann: öffnet sich die Entitäts-Detailansicht

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
