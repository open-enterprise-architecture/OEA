---
id: REQ-107
title: ABB anlegen und Verfeinerungs-Hierarchie verwalten
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
  stakeholders:
    - SH-03
  adrs:
    - adrs/ADR-002-continuum-repository.md
supersedes: []
superseded_by: []
---

# REQ-107: ABB anlegen und Verfeinerungs-Hierarchie verwalten

## Aussage

Das System MUSS das Anlegen, Bearbeiten und Löschen von `ArchitectureBuildingBlock`-Einträgen (ABBs) mit `scope=organization` ermöglichen. Pflichtfelder: `name` (eindeutig je `continuumLevel`), `continuumLevel`, `maturityLevel`, `governanceStatus`. Optional: `specifications` (Markdown), `industry` (Pflicht wenn `continuumLevel=industry`), `refines` (Verweis auf bestehenden ABB; Dropdown zeigt nur Einträge ohne Zyklusrisiko). Beim Speichern MUSS das System Zyklen im Verfeinerungs-Graphen erkennen und mit HTTP 422 ablehnen.

## Begründung

ABBs sind die zentralen Spezifikations-Bausteine des Enterprise Continuum. Ohne CRUD-Operationen und Verfeinerungs-Hierarchie kann keine organisationsspezifische Continuum-Bibliothek aufgebaut werden. Die Zyklus-Erkennung ist notwendig, um inkonsistente Verfeinerungs-Ketten zu verhindern, die spätere Graph-Traversierungen korrumpieren würden.

## Akzeptanzkriterien

**AC1** (ABB anlegen):
- Wenn: ein ABB mit `continuumLevel=organization` und `maturityLevel=established` angelegt wird
- Dann: antwortet das System mit HTTP 201; der ABB ist in der Bibliothek sichtbar

**AC2** (Zyklus-Erkennung):
- Wenn: ABB „B" auf `refines=A` gesetzt ist und anschließend ABB „A" auf `refines=B` gesetzt werden soll
- Dann: antwortet das System mit HTTP 422 „Zyklus erkannt: A → B → A" und lehnt die Speicherung ab

**AC3** (Pflichtfeld industry):
- Wenn: ein ABB mit `continuumLevel=industry` ohne das Feld `industry` gespeichert werden soll
- Dann: antwortet das System mit HTTP 422

## Abhängigkeiten

- **Voraussetzungen**: REQ-111 (Scope-Schutz für importierte Bausteine)
- **Folgewirkungen**: REQ-110 (Reference Architecture komposiert ABBs), REQ-118 (ABB-Abdeckungs-Analyse)

## Realisierungs-Hinweise

- Zyklus-Erkennung: DFS oder Kahn's Algorithm auf dem ABB-Verfeinerungs-Graphen vor jedem Speichern
- Dropdown für `refines`: nur ABBs ohne transitiven Verweis auf den aktuell bearbeiteten ABB anzeigen
- `name` muss per Unique-Constraint je `continuumLevel` in der DB abgesichert sein

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
