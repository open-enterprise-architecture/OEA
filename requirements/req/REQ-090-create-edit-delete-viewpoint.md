---
id: REQ-090
title: Viewpoint anlegen, bearbeiten und löschen
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-12
  business_objects:
    - viewpoint
    - metamodel-configuration
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-090: Viewpoint anlegen, bearbeiten und löschen

## Aussage

Das System MUSS das Anlegen, Bearbeiten und Löschen von user-defined `ViewpointDefinition`-Einträgen ermöglichen. Ein Viewpoint besteht aus: `name` (Pflicht, eindeutig in der Instanz), `notation` (Pflicht: `archimate3|uml|bpmn20`), `allowedEntityTypes[]`, `allowedConnectionTypes[]` und `notationMappings[]` (pro EntityType: `notationElement`, `defaultWidth`, `defaultHeight`). Bearbeitung erfolgt in einem dreistufigen Formular: Basisfelder → Typ-Auswahl → Notation-Mappings.

## Begründung

Organisationen haben spezifische Sichtweisen (Cloud Security, Domänen-Übersicht), die sich nicht in Standard-Viewpoints abbilden lassen. User-defined Viewpoints ermöglichen die Anpassung des Werkzeugs an organisationsspezifische Notationskonventionen.

## Akzeptanzkriterien

**AC1** (Viewpoint anlegen):
- Wenn: ein neuer Viewpoint mit 3 EntityTypes angelegt wird
- Dann: antwortet die API mit HTTP 201; der Viewpoint ist sofort für neue Diagramme auswählbar

**AC2** (EntityType entfernen mit Warnung):
- Wenn: beim Bearbeiten ein EntityType aus `allowedEntityTypes` entfernt wird, der in bestehenden Diagrammen genutzt wird
- Dann: zeigt das System eine Warnung "N Diagramme betroffen"; die Bearbeitung wird nicht blockiert

**AC3** (Viewpoint löschen):
- Wenn: ein user-defined Viewpoint gelöscht wird
- Dann: wird er aus der `MetamodelConfiguration` entfernt

## Abhängigkeiten

- **Voraussetzungen**: REQ-032 (EntityTypeDefinition vorhanden); REQ-058/059 (Export/Import bereits vorhanden)
- **Folgewirkungen**: REQ-091 (Notation-Mapping), REQ-092 (system-defined Schreibschutz), REQ-093 (Lösch-Warnung)

## Realisierungs-Hinweise

- Dreistufiges Formular mit Schritt-Indikatoren im UI
- Eindeutigkeit des `name`-Feldes per DB-Constraint auf Instanzebene sicherstellen
- `allowedEntityTypes` und `allowedConnectionTypes` als Array-Felder in der DB

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
