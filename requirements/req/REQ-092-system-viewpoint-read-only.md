---
id: REQ-092
title: System-definierte Viewpoints schreibgeschützt
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-12
  business_objects:
    - viewpoint
    - metamodel-configuration
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-092: System-definierte Viewpoints schreibgeschützt

## Aussage

Viewpoints mit `viewpointType=system-defined` MÜSSEN für alle Schreib-Operationen gesperrt sein. In der UI DÜRFEN Bearbeiten- und Löschen-Aktionen für system-defined Viewpoints nicht verfügbar sein. API-Versuche MÜSSEN mit HTTP 422 abgewiesen werden.

## Begründung

System-definierte Viewpoints (ArchiMate 3, UML, BPMN 2.0) sind Teil der OEA-Grundfunktionalität. Ihre Veränderung würde das Rendering-System beschädigen und zu inkonsistenten Diagramm-Darstellungen führen.

## Akzeptanzkriterien

**AC1** (API-Schreibschutz):
- Wenn: ein DELETE-Request an `/viewpoints/{system-defined-id}` gesendet wird
- Dann: antwortet die API mit HTTP 422

**AC2** (UI-Schreibschutz):
- Wenn: system-defined Viewpoints in der Liste angezeigt werden
- Dann: sind Bearbeiten- und Löschen-Buttons nicht sichtbar

**AC3** (Import-Schreibschutz):
- Wenn: beim Import (REQ-059) system-defined Viewpoints enthalten sind
- Dann: werden diese nicht überschrieben

## Abhängigkeiten

- **Voraussetzungen**: REQ-090 (Viewpoint-Verwaltung)
- **Folgewirkungen**: keine

## Realisierungs-Hinweise

- `viewpointType`-Feld in der DB; Guard-Logik im Service-Layer prüft Typ vor jeder Schreib-Operation
- UI rendert Aktions-Buttons konditional basierend auf `viewpointType`
- System-defined Viewpoints werden bei DB-Migration initial befüllt und nicht durch Nutzeraktionen überschreibbar

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
