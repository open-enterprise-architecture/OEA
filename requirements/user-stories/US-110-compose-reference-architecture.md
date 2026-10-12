# US-110: Reference Architecture aus Bausteinen komponieren

**ID**: US-110
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich `ReferenceArchitecture`-Einträge aus ABBs und Patterns komponieren und als Blueprint für Plateaus freigeben, damit wiederverwendbare Architekturvorgaben die Planungsarbeit beschleunigen.

## Bezug

**Use Case**: [UC-17](../use-cases/UC-17-continuum-bausteine-verwalten.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-110](../req/REQ-110-reference-architecture-komponieren.md)

## Akzeptanzkriterien

**AC1** (Reference Architecture mit Blueprint-Freigabe):
- Wenn: ich eine Reference Architecture mit 3 ABBs, 1 Pattern und `governanceStatus=approved` anlege
- Dann: speichert das System sie mit HTTP 201; sie ist als Blueprint in UC-11 (Plateau-Anlage) auswählbar

**AC2** (Leere Kompositions-Warnung):
- Wenn: ich eine Reference Architecture ohne ABBs und ohne Patterns speichern möchte
- Dann: zeigt das System eine Governance-Warnung; die Speicherung wird nicht blockiert

**AC3** (Pflichtfeld targetIndustry):
- Wenn: ich eine Reference Architecture mit `continuumLevel=industry` ohne `targetIndustry` speichere
- Dann: erhalte ich HTTP 422

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
