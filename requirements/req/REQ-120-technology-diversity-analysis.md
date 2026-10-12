---
id: REQ-120
title: Technology-Diversity-Analyse mit Executive-Summary-Export
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
    - trm-category
    - solution-building-block
    - entity
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-120: Technology-Diversity-Analyse mit Executive-Summary-Export

## Aussage

Das System MUSS eine Technology-Diversity-Analyse anbieten, die je TRM-Kategorie die Anzahl unterschiedlicher, tatsächlich genutzter SBBs (via `entity.instanceOfSBBId`) ausweist. TRM-Kategorien mit mehr als einem genutzten SBB MÜSSEN als „Standardisierungspotenzial" hervorgehoben werden. TRM-Kategorien ohne konfigurierten `preferredStandard` MÜSSEN separat aufgelistet werden. Die Analyse MUSS als PDF oder CSV exportierbar sein und eine Executive-Summary-Sektion (Top-3-Risiken: prohibited Entitäten, Gap-Quote, Diversity-Hotspots) enthalten.

## Begründung

Technology Diversity ist ein häufiger Kostentreiber. Die Sichtbarkeit von Kategorien mit vielen verschiedenen Produkten ermöglicht gezielte Standardisierungs-Initiativen und liefert Entscheidungsträgern export-fähige Analysegrundlagen.

## Akzeptanzkriterien

**AC1** (Standardisierungspotenzial hervorheben):
- Wenn: die TRM-Kategorie „Container Orchestration" 3 unterschiedliche genutzte SBBs aufweist
- Dann: wird diese Kategorie als „Standardisierungspotenzial" hervorgehoben

**AC2** (Fehlende Standards auflisten):
- Wenn: TRM-Kategorien ohne konfigurierten `preferredStandard` vorhanden sind
- Dann: erscheinen diese in einer separaten Liste „Fehlende Standards"

**AC3** (PDF-Export mit Executive Summary):
- Wenn: der Export als PDF ausgelöst wird
- Dann: enthält das Dokument eine Executive-Summary-Sektion mit Top-3-Risiken (prohibited Entitäten, Gap-Quote, Diversity-Hotspots) und Detailtabellen je Analyse-Sektion

## Abhängigkeiten

- **Voraussetzungen**: REQ-118 (Gap-Quote für Executive Summary), REQ-119 (prohibited Entitäten für Executive Summary), REQ-116 (preferredStandard)
- **Folgewirkungen**: keine

## Realisierungs-Hinweise

- Diversity-Berechnung: `COUNT(DISTINCT entity.instance_of_sbb_id) GROUP BY entity.trm_category_id`
- PDF-Export: Server-seitige Generierung (z.B. via Puppeteer oder WeasyPrint); CSV via Stream
- Executive Summary: Top-3 nach Schweregrad; prohibited > Gap > Diversity-Hotspot

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
