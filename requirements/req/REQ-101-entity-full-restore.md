---
id: REQ-101
title: Vollwiederherstellung einer Entität auf früheren Stand
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-15
  business_objects:
    - entity
    - entity-version
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-101: Vollwiederherstellung einer Entität auf früheren Stand

## Aussage

Das System MUSS die Wiederherstellung einer ArchitectureEntity auf einen beliebigen früheren Stand ermöglichen. Vor der Ausführung MUSS ein Bestätigungsdialog angezeigt werden, der den Diff (aktuell vs. Zielversion) und den optionalen Änderungsgrund enthält. Unveränderliche Felder (`id`, `entityTypeId`, `sourceEntityId`, `targetEntityId`, `createdAt`, `createdBy`) DÜRFEN dabei nicht verändert werden.

## Begründung

Fehlerhafte Änderungen passieren. Ohne Wiederherstellungsmöglichkeit müssen Änderungen manuell rückgängig gemacht werden, was fehleranfällig ist und keine Audit-Spur hinterlässt.

## Akzeptanzkriterien

**AC1** (Wiederherstellung auf früheren Stand):
- Wenn: eine Entität auf Version v3 wiederhergestellt wird
- Dann: sind `name`, `description` und `properties` auf v3-Werte gesetzt; `id` ist unverändert

**AC2** (Bestätigungsdialog mit Diff):
- Wenn: die Wiederherstellung initiiert wird
- Dann: zeigt der Bestätigungsdialog, welche Felder sich zwischen aktuellem Stand und Zielversion unterscheiden

**AC3** (Abbruch ohne Änderung):
- Wenn: der Dialog abgebrochen wird
- Dann: bleibt die Entität unverändert

## Abhängigkeiten

- **Voraussetzungen**: REQ-102 (Snapshot-Sicherung vor Wiederherstellung), REQ-103 (atomare Transaktion)
- **Folgewirkungen**: REQ-102 (Audit-Snapshot nach Wiederherstellung)

## Realisierungs-Hinweise

- Unveränderliche Felder: `id`, `entityTypeId`, `sourceEntityId`, `targetEntityId`, `createdAt`, `createdBy` — aus Snapshot ignorieren
- Diff-Berechnung wie REQ-099 (Dot-Notation, feldweise)
- Änderungsgrund in `EntityVersion.changeReason` persistieren

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
