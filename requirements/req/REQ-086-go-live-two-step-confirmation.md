---
id: REQ-086
title: Go-Live zweistufige Bestätigung
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

# REQ-086: Go-Live zweistufige Bestätigung

## Aussage

Vor der Ausführung eines Go-Live-Übergangs MUSS das System einen Bestätigungsdialog anzeigen, der (1) den Diff zusammenfasst (P1→baseline, P0→realized), (2) nicht-implemented Solutions und offene CEL-Violations als Warnungen auflistet und (3) die manuelle Eingabe des exakten Plateau-Namens als zweite Bestätigungsstufe erfordert.

## Begründung

Go-Live ist irreversibel. Eine zweistufige Bestätigung verhindert versehentliche Übergänge bei einer hochkritischen Operation, die das gesamte Architektur-Repository in einen neuen Produktivzustand versetzt.

## Akzeptanzkriterien

**AC1** (Button deaktiviert ohne Namenseingabe):
- Wenn: der Bestätigungsdialog ohne Namenseingabe angezeigt wird
- Dann: ist der "Go-Live bestätigen"-Button deaktiviert

**AC2** (Falscher Name):
- Wenn: ein falscher Plateau-Name eingegeben wird
- Dann: bleibt der Button deaktiviert; keine Fehlermeldung ist erforderlich

**AC3** (Korrekter Name):
- Wenn: der exakte Plateau-Name eingegeben wird
- Dann: wird der Button aktiviert und Go-Live kann ausgeführt werden

## Abhängigkeiten

- **Voraussetzungen**: REQ-084 (Plateau existiert), REQ-087 (Warnungen ohne Blocking)
- **Folgewirkungen**: REQ-088 (atomare Transition)

## Realisierungs-Hinweise

- Namens-Validierung im Frontend per Echtzeit-Vergleich (keine API-Anfrage nötig)
- Diff-Anzeige: P1 wird zu baseline, P0 wird zu realized — beide Änderungen benennen
- CEL-Violations und nicht-implemented Solutions als separate Warnungsabschnitte

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
