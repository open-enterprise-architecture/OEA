---
id: REQ-106
title: Teilwiederherstellung restoredFields Protokoll
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-16
  business_objects:
    - entity
    - entity-version
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-106: Teilwiederherstellung restoredFields Protokoll

## Aussage

Bei jeder Teilwiederherstellung MUSS der neue EntityVersion-Eintrag die exakte Liste der wiederhergestellten Felder in Dot-Notation als `restoredFields` enthalten (z.B. `["description", "properties.owner"]`). Eine Teilwiederherstellung ohne ausgewählte Felder (`restoredFields` leer) MUSS abgewiesen werden. In der Zeitlinie (UC-14) MUSS der Eintrag als "Teilweise wiederhergestellt aus vN (field1, field2)" lesbar sein.

## Begründung

Ohne `restoredFields`-Protokoll ist in der Zeitlinie nicht erkennbar, was bei einer Teilwiederherstellung tatsächlich geändert wurde — der Audit-Trail wäre unvollständig und für Compliance-Zwecke unbrauchbar.

## Akzeptanzkriterien

**AC1** (restoredFields im Versionseintrag):
- Wenn: eine Teilwiederherstellung mit `description` und `properties.owner` ausgeführt wird
- Dann: enthält der neue EntityVersion-Eintrag `restoredFields=["description","properties.owner"]`

**AC2** (Leere Auswahl abweisen):
- Wenn: keine Felder ausgewählt wurden und der Bestätigen-Button geklickt wird
- Dann: ist der Button deaktiviert; ein API-Aufruf mit leerem `restoredFields` wird mit HTTP 422 abgewiesen

**AC3** (Zeitlinie-Lesbarkeit):
- Wenn: die Zeitlinie nach einer Teilwiederherstellung angezeigt wird
- Dann: zeigt der Eintrag "Teilweise wiederhergestellt aus v4 (description, properties.owner)"

## Abhängigkeiten

- **Voraussetzungen**: REQ-102 (Audit-Snapshot), REQ-103 (atomare Transaktion), REQ-105 (Feldauswahl)
- **Folgewirkungen**: keine

## Realisierungs-Hinweise

- `restoredFields`-Array als JSONB-Spalte in `entity_versions`-Tabelle
- API-Validierung: `restoredFields` darf nicht leer sein; HTTP 422 bei leerem Array
- Zeitlinie-Darstellung: Feld-Liste als aufklappbarer Tooltip für lange Listen

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
