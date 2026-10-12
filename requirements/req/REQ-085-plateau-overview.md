---
id: REQ-085
title: Plateau-Übersicht nach Status gruppiert
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-11
  business_objects:
    - plateau
    - entity
    - solution
    - architecture
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-085: Plateau-Übersicht nach Status gruppiert

## Aussage

Das System MUSS Plateaus in einer strukturierten Übersicht nach Status gruppiert anzeigen: aktuelles Baseline (hervorgehoben), Target-Plateaus (mit geplantem `validFrom`), realisierte Plateaus (einklappbar, chronologisch absteigend). Jedes Plateau zeigt Name, Status, `validFrom`/`validTo` und verknüpfte Solutions-Anzahl.

## Begründung

Ohne strukturierte Übersicht kann Kurt nicht schnell erkennen, welcher Zustand aktuell produktiv ist und welche Zielzustände geplant sind. Die Gruppierung nach Status macht den Planungsstand auf einen Blick sichtbar.

## Akzeptanzkriterien

**AC1** (Gruppierte Übersicht):
- Wenn: die Plateau-Übersicht aufgerufen wird und Baseline-, Target- und Realized-Plateaus existieren
- Dann: wird das Baseline hervorgehoben, Targets mit `validFrom` angezeigt und Realized-Plateaus als einklappbare Sektion dargestellt

**AC2** (Leere Target-Sektion):
- Wenn: keine Target-Plateaus existieren
- Dann: wird die Target-Sektion mit dem Hinweis "Noch kein Ziel-Plateau geplant" angezeigt

## Abhängigkeiten

- **Voraussetzungen**: REQ-084 (Plateau muss angelegt werden können)
- **Folgewirkungen**: REQ-086 (Go-Live-Übergang ist über die Übersicht erreichbar)

## Realisierungs-Hinweise

- Paginierung für Realized-Plateaus wenn mehr als 20 Einträge vorhanden
- Solutions-Anzahl als aggregierter Zähler pro Plateau (DB-Query mit JOIN)

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
