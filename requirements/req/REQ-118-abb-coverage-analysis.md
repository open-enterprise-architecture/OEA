---
id: REQ-118
title: ABB-Abdeckungs-Analyse (Gap-Quote)
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
    - entity
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-118: ABB-Abdeckungs-Analyse (Gap-Quote)

## Aussage

Das System MUSS eine ABB-Abdeckungs-Analyse anbieten, die für jeden ABB mit `governanceStatus=approved` ausweist: Anzahl konformer Entitäten (`conformsToABBId`), Anzahl implementierender SBBs und ob ein Gap vorliegt (keine konformen Entitäten). Die Gesamtquote (ABBs mit mindestens einer Instanz / alle approved ABBs) MUSS als Prozentzahl „Gap-Quote" sichtbar sein. Die Analyse ist rein lesend.

## Begründung

ABBs ohne konforme Instanzen zeigen, wo definierte Standards nicht in der Landschaft angekommen sind — die wesentliche Governance-Aussage des Architecture Continuum. Die Gap-Quote ermöglicht Trending über Zeit.

## Akzeptanzkriterien

**AC1** (Gap-Quote-Berechnung):
- Wenn: 5 approved ABBs vorhanden sind, davon 2 ohne Instanz
- Dann: zeigt die Analyse Gap-Quote = 40%; die Tabelle zeigt alle 5 ABBs mit ihren Instanz-Zählungen

**AC2** (ABB-Detailansicht):
- Wenn: ein ABB in der Analyse-Tabelle geöffnet wird
- Dann: sind implementierende SBBs und konforme Entitäten als anklickbare Links sichtbar

**AC3** (Leer-Zustand):
- Wenn: noch keine ABBs konfiguriert sind
- Dann: zeigt das System den Hinweis „Noch keine ABBs konfiguriert; starte mit UC-17"

## Abhängigkeiten

- **Voraussetzungen**: REQ-107 (ABBs), REQ-108 (SBBs)
- **Folgewirkungen**: REQ-120 (Technology-Diversity-Analyse nutzt dieselben Daten für Executive Summary)

## Realisierungs-Hinweise

- Gap-Quote: SQL-Aggregation; kein Caching erforderlich bei erster Version
- Konforme Entitäten: JOIN auf `entity.conformsToABBId`; Links navigieren zur Entitäts-Detailansicht
- Analyse: rein lesender GET-Endpoint; keine Schreiboperationen

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
