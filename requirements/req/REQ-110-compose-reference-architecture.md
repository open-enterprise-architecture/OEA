---
id: REQ-110
title: Reference Architecture aus Bausteinen komponieren
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
    - reference-architecture
    - architecture-building-block
    - architecture-pattern
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-110: Reference Architecture aus Bausteinen komponieren

## Aussage

Das System MUSS das Anlegen, Bearbeiten und Löschen von `ReferenceArchitecture`-Einträgen ermöglichen. Pflichtfelder: `name`, `continuumLevel`, `governanceStatus`. Optional: `description`, `targetIndustry` (Pflicht wenn `continuumLevel=industry`), `composedOf` (ABB-Multi-Select), `basedOnPatterns` (Pattern-Multi-Select). Eine Reference Architecture MUSS mindestens einen ABB oder ein Pattern referenzieren; leere Komposition MUSS eine Governance-Warnung erzeugen (kein Block). `governanceStatus=approved` Reference Architectures MÜSSEN als Blueprint-Option bei der Plateau-Anlage (UC-11) auswählbar sein.

## Begründung

Reference Architectures ohne Bausteine sind Leerformeln. Die Verknüpfung mit Plateau-Anlage aktiviert den Wiederverwendungs-Nutzen des Enterprise Continuum im operativen Planungsprozess.

## Akzeptanzkriterien

**AC1** (Reference Architecture anlegen und als Blueprint verfügbar):
- Wenn: eine Reference Architecture mit 3 ABBs und 1 Pattern angelegt und `governanceStatus=approved` gesetzt wird
- Dann: antwortet das System mit HTTP 201; die Reference Architecture ist als Blueprint in UC-11 (Plateau-Anlage) auswählbar

**AC2** (Leere Kompositions-Warnung):
- Wenn: eine Reference Architecture ohne ABBs und ohne Patterns gespeichert werden soll
- Dann: zeigt das System eine Governance-Warnung; die Speicherung wird nicht blockiert

**AC3** (Pflichtfeld targetIndustry):
- Wenn: eine Reference Architecture mit `continuumLevel=industry` ohne `targetIndustry` gespeichert werden soll
- Dann: antwortet das System mit HTTP 422

## Abhängigkeiten

- **Voraussetzungen**: REQ-107 (ABBs), REQ-109 (Patterns), REQ-111 (Scope-Schutz)
- **Folgewirkungen**: UC-11 (Plateau-Anlage mit Blueprint-Option)

## Realisierungs-Hinweise

- `composedOf` und `basedOnPatterns` als separate Many-to-Many-Relationen
- Blueprint-Verknüpfung zu UC-11: `governanceStatus=approved` Reference Architectures via API-Endpoint abrufbar für Plateau-Dropdown
- Governance-Warnung bei leerer Komposition: UI-Banner, kein API-Fehler

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
