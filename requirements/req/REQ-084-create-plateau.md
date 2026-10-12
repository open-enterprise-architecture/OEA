---
id: REQ-084
title: Plateau anlegen
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

# REQ-084: Plateau anlegen

## Aussage

Das System MUSS das Anlegen von Plateaus mit den Status `baseline`, `target` und `transition` ermöglichen. Pflichtfeld: `name`; optional: `description` (max. 2000 Zeichen), `validFrom`, `succeeds` (Referenz auf bestehendes Plateau). Pro Instanz darf maximal ein Plateau den Status `baseline` haben.

## Begründung

Ohne Plateau-Anlage kann keine strategische Architekturplanung im Plateau-Modus stattfinden. Das Plateau ist die zentrale Einheit zur Strukturierung von Ist- und Zielzuständen im EA-Repository.

## Akzeptanzkriterien

**AC1** (Initiales Baseline anlegen):
- Wenn: ein Plateau ohne `succeeds`-Referenz und mit `status=baseline` angelegt wird
- Dann: antwortet die API mit HTTP 201 und `status=baseline`

**AC2** (Target anlegen):
- Wenn: ein Plateau mit `status=target` und `succeeds`=Baseline-ID angelegt wird
- Dann: antwortet die API mit HTTP 201; `status=target` und `succeeds` sind korrekt gesetzt

**AC3** (Zweites Baseline abweisen):
- Wenn: ein weiteres Plateau mit `status=baseline` angelegt wird, obwohl bereits ein Baseline-Plateau existiert
- Dann: antwortet die API mit HTTP 422 ("Es existiert bereits ein Baseline-Plateau")

## Abhängigkeiten

- **Voraussetzungen**: UC-11 aktiv (Plateau-Modus für die Instanz aktiviert)
- **Folgewirkungen**: REQ-085 (Übersicht), REQ-086 (Go-Live-Bestätigung)

## Realisierungs-Hinweise

- Plateau-Status ist nach Go-Live (`realized`) immutabel; kein erneutes Schreiben erlaubt
- `succeeds`-Referenz ist optional und wird nur bei `target`-Plateaus sinnvoll gesetzt
- Unique-Constraint auf `status=baseline` pro Instanz in der DB absichern

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
