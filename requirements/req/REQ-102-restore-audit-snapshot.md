---
id: REQ-102
title: Audit-Snapshot vor jeder Wiederherstellung
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

# REQ-102: Audit-Snapshot vor jeder Wiederherstellung

## Aussage

Vor jeder Wiederherstellung (vollständig UC-15 oder teilweise UC-16) MUSS das System den aktuellen Entitätszustand als unveränderlichen EntityVersion-Snapshot sichern. Der neue EntityVersion-Eintrag MUSS `restoredFromVersion` (Quellversion) enthalten. Dieser Snapshot MUSS in der Zeitlinie (UC-14) als Wiederherstellungs-Eintrag sichtbar sein.

## Begründung

Jede Wiederherstellung muss selbst nachvollziehbar sein. Ohne Audit-Snapshot würde eine Wiederherstellung den aktuellen Stand unsichtbar löschen — was den Audit-Trail bricht und Compliance-Anforderungen verletzt.

## Akzeptanzkriterien

**AC1** (Snapshot-Eintrag nach Wiederherstellung):
- Wenn: eine Wiederherstellung ausgeführt wird
- Dann: ist ein neuer EntityVersion-Eintrag mit `restoredFromVersion=N` in der Zeitlinie sichtbar

**AC2** (Markierung in Zeitlinie):
- Wenn: die Zeitlinie nach einer Wiederherstellung angezeigt wird
- Dann: zeigt der neue Eintrag die Markierung "Wiederhergestellt aus vN"

**AC3** (Snapshot vor Wiederherstellung erhalten):
- Wenn: die Wiederherstellung abgeschlossen ist
- Dann: ist der Zustand VOR der Wiederherstellung als eigenständiger Snapshot in `entity_versions` vorhanden

## Abhängigkeiten

- **Voraussetzungen**: REQ-098 (Zeitlinie), REQ-103 (atomare Transaktion)
- **Folgewirkungen**: REQ-101 (Vollwiederherstellung), REQ-104–REQ-106 (Teilwiederherstellung)

## Realisierungs-Hinweise

- Snapshot-Erstellung als erster Schritt innerhalb der Transaktion (vor dem Entitäts-Update)
- `restoredFromVersion`-Feld in `entity_versions`-Tabelle ergänzen
- Zeitlinie-Darstellung: Icon/Label für Wiederherstellungs-Einträge

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
