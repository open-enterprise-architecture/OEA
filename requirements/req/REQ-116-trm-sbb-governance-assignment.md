---
id: REQ-116
title: SBB-Governance-Zuordnung je TRM-Kategorie
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-19
  business_objects:
    - trm-category
    - solution-building-block
    - entity
  stakeholders:
    - SH-03
supersedes: []
superseded_by: []
---

# REQ-116: SBB-Governance-Zuordnung je TRM-Kategorie

## Aussage

Das System MUSS für jede TRM-Kategorie (auch `scope=imported`) das Setzen von `preferredStandard` (Einzelauswahl eines SBBs), `acceptedAlternatives` (Multi-Select SBBs) und `deprecatedOptions` (Multi-Select SBBs) ermöglichen. Ein SBB DARF in derselben Kategorie nicht in mehr als einer der drei Listen erscheinen (mutually exclusive). Wenn sich `preferredStandard` ändert, MUSS das System alle Entitäten in dieser TRM-Kategorie, deren `instanceOfSBBId` nicht dem neuen `preferredStandard` entspricht, in der UI mit dem Hinweis „Abweichung vom TRM-Standard" versehen.

## Begründung

Die SBB-Governance-Zuordnung ist der Kern des Technology-Standards-Mechanismus. Ohne sie ist das TRM eine leere Taxonomie ohne Steuerungswirkung auf die Ist-Landschaft.

## Akzeptanzkriterien

**AC1** (preferredStandard setzen und Abweichungs-Hinweis):
- Wenn: `preferredStandard=PostgreSQL 17` in der Kategorie „Database Management" gesetzt wird
- Dann: zeigen alle Entitäten in dieser Kategorie mit einem anderen `instanceOfSBBId` den Hinweis „Abweichung vom TRM-Standard"

**AC2** (Mutually exclusive):
- Wenn: derselbe SBB gleichzeitig in `preferredStandard` und `acceptedAlternatives` gesetzt werden soll
- Dann: antwortet das System mit HTTP 422 „SBB darf nur in einer Liste erscheinen"

**AC3** (imported-Kategorie editierbar):
- Wenn: eine TRM-Kategorie mit `scope=imported` geöffnet wird
- Dann: sind SBB-Zuordnungen editierbar; Name und Parent-Kategorie sind gesperrt

## Abhängigkeiten

- **Voraussetzungen**: REQ-108 (SBBs), REQ-115 (TRM-Kategorien), REQ-111 (Scope-Schutz erlaubt SBB-Zuordnung trotz importierter Kategorie)
- **Folgewirkungen**: REQ-119 (Standards-Drift-Analyse nutzt preferredStandard), REQ-120 (Diversity-Analyse)

## Realisierungs-Hinweise

- Mutually-exclusive-Validierung: Backend prüft alle drei Listen auf Überschneidungen vor Speicherung
- Abweichungs-Hinweis: Berechnung via JOIN auf Entitäts-Tabelle; asynchrone Aktualisierung nach `preferredStandard`-Änderung möglich
- UI: drei separate Dropdown-Felder je Kategorie; SBBs bereits in einer Liste werden in anderen ausgegraut

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
