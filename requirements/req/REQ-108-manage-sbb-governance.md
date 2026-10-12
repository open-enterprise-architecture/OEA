---
id: REQ-108
title: SBB Governance-Status verwalten
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
    - solution-building-block
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-108: SBB Governance-Status verwalten

## Aussage

Das System MUSS das Anlegen, Bearbeiten und Löschen von `SolutionBuildingBlock`-Einträgen (SBBs) mit `scope=organization` ermöglichen. Pflichtfelder: `name`, `governanceStatus` (`[approved, acceptable, deprecated, prohibited]`). Optional: `vendor`, `version`, `description`, `evaluationNotes`, `implements` (Multi-Select bestehender ABBs). Wenn `governanceStatus=prohibited` gesetzt wird, MUSS das System alle Entitäten mit `instanceOfSBBId` = dieser SBB-ID in der UI mit einem Hinweis „Verwendetes Produkt verboten – Migration erforderlich" versehen.

## Begründung

SBBs mit Governance-Status sind der Mechanismus für Technology-Standards-Enforcement. Ohne Prohibited-Warnung auf Entitäten bleibt der Standard wirkungslos, da Architekten keine direkte Sichtbarkeit auf betroffene Landschafts-Elemente haben.

## Akzeptanzkriterien

**AC1** (SBB anlegen):
- Wenn: eine SBB mit `governanceStatus=approved` und `implements=[ABB-UUID]` angelegt wird
- Dann: antwortet das System mit HTTP 201

**AC2** (Prohibited-Warnung auf Entitäten):
- Wenn: eine SBB auf `governanceStatus=prohibited` gesetzt wird
- Dann: zeigen alle Entitäten mit `instanceOfSBBId=dieser-SBB` in der UI den Hinweis „Verwendetes Produkt verboten – Migration erforderlich"

**AC3** (ABB-Referenz-Warnung):
- Wenn: eine SBB ohne `implements`-ABBs angelegt wird
- Dann: zeigt das System eine Governance-Warnung „SBB ohne ABB-Referenz ist nur Produktkatalog-Eintrag" ohne die Speicherung zu blockieren

## Abhängigkeiten

- **Voraussetzungen**: REQ-107 (ABBs müssen existieren für `implements`-Referenz), REQ-111 (Scope-Schutz)
- **Folgewirkungen**: REQ-116 (TRM-SBB-Governance-Zuordnung), REQ-119 (Standards-Drift-Analyse)

## Realisierungs-Hinweise

- `governanceStatus=prohibited` löst einen asynchronen Job aus, der alle `instanceOfSBBId`-Verweise markiert
- Warnung auf Entitäten: UI-Badge, kein API-Block auf der Entitäts-Ebene selbst
- `implements` als Many-to-Many-Relation zwischen SBB und ABB in eigener Join-Tabelle

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
