# US-109: Architecture Pattern anlegen

**ID**: US-109
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich `ArchitecturePattern`-Einträge mit Problembeschreibung und Lösung anlegen, damit die Continuum-Bibliothek wiederverwendbare und vollständige Lösungsmuster enthält.

## Bezug

**Use Case**: [UC-17](../use-cases/UC-17-continuum-bausteine-verwalten.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-109](../req/REQ-109-architecture-pattern-anlegen.md)

## Akzeptanzkriterien

**AC1** (Pattern anlegen):
- Wenn: ich ein Pattern mit `problem` und `solution` anlege
- Dann: speichert das System es mit HTTP 201; es ist in der Bibliothek sichtbar

**AC2** (Pflichtfeld problem):
- Wenn: ich ein Pattern ohne `problem`-Feld speichern möchte
- Dann: erhalte ich HTTP 422 „problem ist ein Pflichtfeld"

**AC3** (relatedPatterns verknüpfen):
- Wenn: ich `relatedPatterns` per Multi-Select mit anderen Patterns verknüpfe
- Dann: werden die Verknüpfungen gespeichert und in der Detailansicht angezeigt

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
