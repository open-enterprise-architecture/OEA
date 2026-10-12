---
id: REQ-109
title: Architecture Pattern anlegen und verwalten
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-17
  business_objects:
    - architecture-pattern
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-109: Architecture Pattern anlegen und verwalten

## Aussage

Das System MUSS das Anlegen, Bearbeiten und Löschen von `ArchitecturePattern`-Einträgen ermöglichen. Pflichtfelder: `name`, `problem` (Markdown), `solution` (Markdown). Optional: `applicability`, `consequences`, `maturityLevel`, `tags`, `relatedPatterns` (Multi-Select). `problem` und `solution` MÜSSEN beide gesetzt sein; ein Pattern ohne Problembeschreibung MUSS mit HTTP 422 abgewiesen werden.

## Begründung

Patterns ohne Problembeschreibung sind keine Patterns — sie sind unverständlich und nicht anwendbar. Die Pflichtfeld-Validierung stellt sicher, dass die Bibliothek ausschließlich vollständige, nutzbare Patterns enthält.

## Akzeptanzkriterien

**AC1** (Pattern anlegen):
- Wenn: ein Pattern mit `problem` und `solution` angelegt wird
- Dann: antwortet das System mit HTTP 201; das Pattern ist in der Bibliothek sichtbar

**AC2** (Pflichtfeld problem):
- Wenn: ein Pattern ohne `problem`-Feld gespeichert werden soll
- Dann: antwortet das System mit HTTP 422 „problem ist ein Pflichtfeld"

**AC3** (relatedPatterns verknüpfen):
- Wenn: `relatedPatterns` per Multi-Select mit anderen Patterns verknüpft werden
- Dann: werden die Verknüpfungen gespeichert und in der Detailansicht angezeigt

## Abhängigkeiten

- **Voraussetzungen**: REQ-111 (Scope-Schutz für importierte Patterns)
- **Folgewirkungen**: REQ-110 (Reference Architecture referenziert Patterns), REQ-118 (Abdeckungs-Analyse)

## Realisierungs-Hinweise

- `problem` und `solution` als LONGTEXT/TEXT-Felder mit Markdown-Rendering in der UI
- `relatedPatterns` als Many-to-Many-Self-Relation; keine Zyklus-Prüfung notwendig
- `tags` als String-Array; Freitext-Eingabe mit Autocomplete aus bestehenden Tags

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
