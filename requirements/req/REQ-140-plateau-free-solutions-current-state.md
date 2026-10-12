---
id: REQ-140
title: Plateau-freie Solutions nur im Aktueller-Stand-Knoten sichtbar
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
    - UC-13
  business_objects:
    - solution
    - plateau
  stakeholders:
    - SH-03
    - SH-04
    - SH-06
  adrs:
    - ADR-020
supersedes: []
superseded_by: []
---

# REQ-140: Plateau-freie Solutions nur im Aktueller-Stand-Knoten sichtbar

## Aussage

Solutions im Projekt-Modus (beide Plateau-IDs `null`) DÜRFEN NICHT als eigenständige Top-Level-Knoten im Browser erscheinen. Ihre implementierten Inhalte MÜSSEN in den virtuellen „Aktueller Stand"-Knoten einfliessen. Solutions im Plateau-Modus erscheinen zusätzlich unter ihrem jeweiligen Plateau-Knoten.

## Begründung

Der „Aktueller Stand" ist der aggregierte Gesamtzustand der Architektur — gleichgültig ob Inhalte über Plateaus oder Projekt-Solutions entstanden sind. Plateau-freie Solutions separat aufzulisten würde den Nutzern eine falsche Trennung suggerieren: Die Inhalte sind Teil der Landschaft, egal durch welchen Governance-Prozess sie entstanden.

## Typ-spezifische Felder

**Verarbeitung**:
1. Solution mit `fromPlateauId=null` AND `toPlateauId=null` (Projekt-Modus):
   - `status=implemented`: Inhalte fliessen in „Aktueller Stand" → kein eigener Top-Level-Knoten
   - `status ∈ {draft, proposed, approved, in-progress}`: Solution erscheint NICHT im Browser (nur in der Solution-Verwaltungsansicht)
2. Solution mit Plateau-Referenzen (Plateau-Modus):
   - Erscheint unter dem entsprechenden Plateau-Knoten
   - Zusätzlich fliessen implementierte Inhalte in „Aktueller Stand"

## Akzeptanzkriterien

**AC1** — Projekt-Modus: kein eigener Knoten:
- Gegeben: Solution A (Projekt-Modus, `status=implemented`), Solution B (Plateau-Modus, IST→SOLL)
- Wenn: Browser-Panel geöffnet wird
- Dann: Solution A erscheint NICHT als eigener Knoten; Solution B erscheint unter Plateau-Knoten

**AC2** — Inhalte in Aktueller Stand:
- Gegeben: Solution A (Projekt-Modus, `status=implemented`) mit 3 Komponenten
- Wenn: „Aktueller Stand" im Browser aufgeklappt wird
- Dann: Die 3 Komponenten sind unter „Aktueller Stand → Komponenten" sichtbar

**AC3** — Nicht-implementierte Projekt-Solutions unsichtbar:
- Gegeben: Solution C (Projekt-Modus, `status=in-progress`)
- Wenn: Browser-Panel angezeigt wird
- Dann: Solution C ist weder im „Aktueller Stand" noch anderswo im Browser sichtbar

## Verifikationsmethode

- [ ] Methode: test (automatisiert)
- [ ] Test-Setup: Solutions in beiden Modi und verschiedenen Status
- [ ] Bestanden-Kriterium: AC1–AC3 alle grün

## Abhängigkeiten

- **Voraussetzungen**: REQ-138; ADR-020; Solution BO (BR-01: beide IDs entweder gesetzt oder null)

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
