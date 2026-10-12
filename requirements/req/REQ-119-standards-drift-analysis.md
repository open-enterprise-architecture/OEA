---
id: REQ-119
title: Standards-Drift-Analyse (prohibited und Abweichungs-Report)
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-20
  business_objects:
    - architecture-building-block
    - solution-building-block
    - trm-category
    - entity
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-119: Standards-Drift-Analyse (prohibited und Abweichungs-Report)

## Aussage

Das System MUSS eine Standards-Drift-Analyse anbieten, die alle Entitäten ausweist, deren `instanceOfSBBId` auf einen SBB mit `governanceStatus=prohibited` oder `deprecated` zeigt. Zusätzlich MUSS die Analyse Entitäten zeigen, deren `instanceOfSBBId` nicht dem `preferredStandard` der zugeordneten TRM-Kategorie entspricht (Abweichungs-Report). Jede Zeile in der Ergebnisliste MUSS einen Direkt-Link zur Entitäts-Detailansicht enthalten. Die Analyse ist rein lesend.

## Begründung

Standards-Drift macht definierten Governance unbemerkt unwirksam. Automatische Erkennung ist die Voraussetzung für gezielte Migrations-Planung und das frühzeitige Erkennen von Compliance-Risiken.

## Akzeptanzkriterien

**AC1** (prohibited-Entitäten):
- Wenn: 3 Entitäten `instanceOfSBBId=prohibited-SBB-UUID` haben
- Dann: sind alle 3 in der Drift-Liste; `prohibited` ist rot hervorgehoben

**AC2** (Abweichungs-Report):
- Wenn: eine Entität einen SBB verwendet, der nicht dem `preferredStandard` ihrer TRM-Kategorie entspricht
- Dann: erscheint die Entität in der Abweichungs-Liste

**AC3** (Direkt-Link):
- Wenn: ein Eintrag in der Drift-Liste angeklickt wird
- Dann: öffnet sich die Entitäts-Detailansicht

## Abhängigkeiten

- **Voraussetzungen**: REQ-108 (SBB mit governanceStatus), REQ-116 (preferredStandard je TRM-Kategorie)
- **Folgewirkungen**: REQ-120 (prohibited-Entitäten fließen in Executive Summary ein)

## Realisierungs-Hinweise

- SQL: LEFT JOIN entity auf solution_building_block; Filter auf `governance_status IN ('prohibited', 'deprecated')`
- Abweichungs-Report: JOIN auf trm_category via entity.trmCategoryId; Vergleich mit `preferred_standard_sbb_id`
- Farbkodierung: `prohibited` = rot, `deprecated` = orange; via CSS-Klassen gesteuert

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
