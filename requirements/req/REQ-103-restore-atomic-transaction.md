---
id: REQ-103
title: Wiederherstellung als atomare Transaktion
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

# REQ-103: Wiederherstellung als atomare Transaktion

## Aussage

Snapshot-Sicherung, Entitäts-Update und Schreiben des neuen EntityVersion-Eintrags MÜSSEN bei jeder Wiederherstellung (UC-15 und UC-16) in einer einzigen DB-Transaktion erfolgen. Bei technischem Fehler MUSS ein vollständiger Rollback erfolgen; kein Inkonsistenz-Zustand (Snapshot ohne Update oder Update ohne Snapshot) ist zulässig.

## Begründung

Partielles Commit würde entweder einen verlorenen Audit-Snapshot oder eine ungültige Entität erzeugen. Beide Zustände beschädigen den Audit-Trail dauerhaft und können nicht automatisch erkannt oder repariert werden.

## Akzeptanzkriterien

**AC1** (Rollback bei DB-Fehler):
- Wenn: ein simulierter DB-Fehler nach dem Snapshot-Schreiben auftritt
- Dann: erfolgt ein vollständiger Rollback; Entität ist unverändert; kein Snapshot in `entity_versions`

**AC2** (Erfolgreiche Wiederherstellung):
- Wenn: die Wiederherstellung erfolgreich abgeschlossen wird
- Dann: sind Snapshot, Entitäts-Update und Versionseintrag alle vorhanden und konsistent

## Abhängigkeiten

- **Voraussetzungen**: keine (fundamentales technisches Requirement)
- **Folgewirkungen**: REQ-101 (Vollwiederherstellung), REQ-102 (Audit-Snapshot), REQ-106 (Teilwiederherstellung)

## Realisierungs-Hinweise

- Spring `@Transactional` mit explizitem Rollback auf alle Exceptions
- Isolation mindestens `REPEATABLE READ`
- Reihenfolge in der Transaktion: (1) Snapshot, (2) Entitäts-Update, (3) EntityVersion-Eintrag

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
