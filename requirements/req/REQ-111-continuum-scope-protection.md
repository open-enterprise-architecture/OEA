---
id: REQ-111
title: Schreibschutz für importierte und built-in Continuum-Bausteine
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-17
  business_objects:
    - architecture-building-block
    - solution-building-block
    - architecture-pattern
    - reference-architecture
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-111: Schreibschutz für importierte und built-in Continuum-Bausteine

## Aussage

Alle Continuum-Bausteine (ABB, SBB, Pattern, Reference Architecture) mit `scope=imported` oder `scope=built-in` MÜSSEN für alle Schreib-Operationen gesperrt sein. In der UI DÜRFEN Bearbeiten- und Löschen-Aktionen für diese Bausteine nicht sichtbar sein; API-Versuche MÜSSEN mit HTTP 422 abgewiesen werden. `scope=imported`-Bausteine MÜSSEN mit einem Badge „Importiert – [sourcePackage]" erkennbar sein.

## Begründung

Importierte Bausteine sind die unveränderliche Grundlage der Continuum-Bibliothek. Änderungen würden Paket-Updates korrumpieren und die Traceability zu den Ursprungs-Frameworks brechen.

## Akzeptanzkriterien

**AC1** (API-Schreibschutz):
- Wenn: ein PUT- oder DELETE-Request auf einen ABB mit `scope=imported` abgesetzt wird
- Dann: antwortet das System mit HTTP 422

**AC2** (UI-Schreibschutz und Badge):
- Wenn: ein Baustein mit `scope=imported` in der UI angezeigt wird
- Dann: sind Bearbeiten- und Löschen-Buttons nicht sichtbar; ein Badge „Importiert – TOGAF 10 TRM" ist sichtbar

**AC3** (SBB-Zuordnungen editierbar):
- Wenn: eine TRM-Kategorie mit `scope=imported` geöffnet wird
- Dann: sind SBB-Zuordnungen (`preferredStandard`, `acceptedAlternatives`, `deprecatedOptions`) editierbar; Name und Parent-Kategorie sind gesperrt

## Abhängigkeiten

- **Voraussetzungen**: REQ-112 (Import-Mechanismus erzeugt scope=imported)
- **Folgewirkungen**: REQ-107, REQ-108, REQ-109, REQ-110 (alle Baustein-CRUDs müssen Scope prüfen)

## Realisierungs-Hinweise

- Middleware-Guard auf allen PUT/PATCH/DELETE-Endpunkten: `scope` prüfen vor Datenbankoperation
- UI: `scope`-Feld aus API-Response bestimmt sichtbare Aktionen (keine client-seitige Sicherheit allein)
- Badge-Inhalt: `sourcePackage`-Feld aus dem Baustein-Datensatz

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
