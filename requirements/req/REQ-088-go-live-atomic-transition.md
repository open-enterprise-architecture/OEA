---
id: REQ-088
title: Go-Live als atomare DB-Transaktion
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

# REQ-088: Go-Live als atomare DB-Transaktion

## Aussage

Die Go-Live-Transition MUSS als atomare DB-Transaktion ausgeführt werden und folgende Änderungen umfassen: (1) P1: `status→baseline`, `validFrom=jetzt(UTC)`; (2) P0: `status→realized`, `validTo=jetzt(UTC)`, read-only; (3) alle Entitäten in P1 mit `lifecycle_state=retiring` → `lifecycle_state=retired`. Bei technischem Fehler MUSS ein vollständiger Rollback erfolgen; kein Inkonsistenz-Zustand ist zulässig.

## Begründung

Der Go-Live-Übergang betrifft mehrere Tabellen. Partielles Commit würde zu inkonsistentem Architekturstatus führen — P1 wäre Baseline ohne P0 als realized, was den Architektur-Lebenszyklus dauerhaft beschädigt.

## Akzeptanzkriterien

**AC1** (Rollback bei DB-Fehler):
- Wenn: ein simulierter DB-Fehler nach dem P1-Update auftritt
- Dann: erfolgt ein vollständiger Rollback; P0 und P1 sind unverändert

**AC2** (Erfolgreicher Go-Live):
- Wenn: Go-Live erfolgreich ausgeführt wird
- Dann: hat P1 `status=baseline`, P0 hat `status=realized`; alle `retiring`-Entitäten in P1 haben `lifecycle_state=retired`; alle Zustände sind konsistent

## Abhängigkeiten

- **Voraussetzungen**: REQ-086 (Bestätigung erteilt), REQ-084 (Plateau existiert)
- **Folgewirkungen**: REQ-089 (Realized-Plateau ist read-only)

## Realisierungs-Hinweise

- Transaktions-Isolation mindestens `REPEATABLE READ`
- Spring `@Transactional` mit explizitem Rollback auf alle Exceptions
- UTC-Zeitstempel für `validFrom`/`validTo` aus Systemzeit zum Transaktionsbeginn

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
