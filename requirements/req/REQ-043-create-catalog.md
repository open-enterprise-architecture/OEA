---
id: REQ-043
title: Katalog anlegen
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-06
  business_objects:
    - catalog
  business_rules:
    - catalog:BR-01
  stakeholders:
    - SH-03
    - SH-07
  concept:
    - concept/20-entities/06-kern-entitaetstypen.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-043: Katalog anlegen

## Aussage

Das System MUSS dem Katalog-Manager ermöglichen, einen neuen [Catalog](../../business-objects/catalog.md) mit folgenden Feldern anzulegen: `name` (Pflicht, eindeutig innerhalb der Instanz), `description` (optional), `primaryEntityType` (Pflicht, muss ein gültiger EntityType-Name aus der MetamodelConfiguration sein), `scope` (Pflicht: `instance` | `personal`) und `defaultJoinMode` (Pflicht: `rows` | `aggregate`, Default: `aggregate`). Der Catalog wird initial ohne Spalten, Joins, Filter oder Views erstellt; Spalten- und Join-Konfiguration erfolgt in separaten Schritten (REQ-044, REQ-045).

## Begründung

Ohne persistierte Catalog-Objekte kann keine wiederverwendbare Tabellenansicht auf das Repository bereitgestellt werden. Das Anlegen eines Catalogs ist der Einstiegspunkt für alle weiteren Konfigurationsschritte. Die Eindeutigkeitsprüfung des Namens verhindert Verwechslungen in der Katalog-Übersicht.

## Kontext

Ein Catalog ist ein Konfigurationsobjekt – er speichert die Abfrage-Definition, nicht die Abfrage-Ergebnisse selbst. Die Live-Ergebnisse werden erst bei Öffnen des Catalogs berechnet (REQ-046). Ein Catalog mit `scope=personal` ist nur für den Ersteller sichtbar; bei `scope=instance` können alle Nutzer der Instanz den Catalog öffnen (aber nicht konfigurieren, sofern sie keine Schreibberechtigung haben).

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:

| Feld | Typ | Optional | Beschreibung |
|---|---|---|---|
| name | string | Pflicht | Lesbarer Name, max. 255 Zeichen; eindeutig innerhalb der Instanz (case-insensitiv, getrimmt) |
| description | string | optional | Kurzbeschreibung, max. 1000 Zeichen |
| primaryEntityType | string | Pflicht | EntityType-Name aus der MetamodelConfiguration (built-in oder custom) |
| scope | enum | Pflicht | `instance` (für alle sichtbar) oder `personal` (nur für Ersteller) |
| defaultJoinMode | enum | Pflicht | `aggregate` (Standard) oder `rows` |

**Verarbeitung**:

1. Name auf Eindeutigkeit prüfen (scope = gesamte Instanz; personal-Catalogs und instance-Catalogs teilen denselben Namensraum pro Instanz, sodass keine Verwechslungen entstehen)
2. `primaryEntityType` gegen MetamodelConfiguration validieren; 422 wenn nicht vorhanden
3. Catalog mit `createdBy = aktuelle Person`, `createdAt = now()` persistieren
4. Leere Listen für `columns`, `joinDefinitions`, `savedFilters`, `savedViews` initialisieren

**Ausgaben**:

- HTTP 201 Created mit dem angelegten Catalog-Objekt (inkl. generierter `id`)
- HTTP 422 Unprocessable Entity bei Validierungsfehlern (fehlende Pflichtfelder, ungültiger EntityType, Name-Kollision)
- HTTP 403 Forbidden wenn der Akteur keine Schreibberechtigung für Catalogs hat

## Akzeptanzkriterien

**AC1** (Anlegen, Happy Path):
- Gegeben: Kurt ist eingeloggt mit Rolle „Enterprise Architekt"; die MetamodelConfiguration enthält den EntityType `ApplicationComponent`
- Wenn: `POST /api/v1/catalogs` mit `{ name: "Application Inventory", primaryEntityType: "ApplicationComponent", scope: "instance", defaultJoinMode: "aggregate" }`
- Dann: HTTP 201; Response enthält id, name, primaryEntityType, scope, defaultJoinMode, createdBy=Kurts ID, leere columns/joinDefinitions/savedFilters/savedViews-Listen

**AC2** (Name-Kollision):
- Gegeben: Ein Catalog mit name="Application Inventory" existiert bereits
- Wenn: Erneuter POST mit demselben Name (egal ob identische Schreibweise oder Variationen wie Leerzeichen vorn/hinten)
- Dann: HTTP 422 mit Fehlermeldung „Ein Katalog mit diesem Namen existiert bereits"

**AC3** (Ungültiger EntityType):
- Gegeben: Die MetamodelConfiguration enthält keinen EntityType „UnbekannterTyp"
- Wenn: POST mit `primaryEntityType: "UnbekannterTyp"`
- Dann: HTTP 422 „Der EntityType ‹UnbekannterTyp› ist in der MetamodelConfiguration nicht definiert"

**AC4** (Scope=personal):
- Gegeben: Sabine legt einen Catalog mit scope=personal an
- Wenn: Franz (anderer Nutzer) die Catalog-Übersicht abruft
- Dann: Sabines personal-Catalog erscheint NICHT in Franzens Sicht; 404 bei direktem Zugriff

**AC5** (Fehlende Berechtigung):
- Gegeben: Franz hat nur die Rolle „Betrachter"
- Wenn: Franz versucht, einen Catalog anzulegen
- Dann: HTTP 403 Forbidden

## Abhängigkeiten

- Blockiert durch: UC-04 / REQ-032 (MetamodelConfiguration muss EntityTypes enthalten)
- Ermöglicht: REQ-044 (Spalten konfigurieren), REQ-045 (Join konfigurieren), REQ-046 (Abfrage ausführen)

## Realisierungs-Hinweise

- Endpoint: `POST /api/v1/catalogs`
- Name-Eindeutigkeit: `SELECT id FROM catalogs WHERE lower(trim(name)) = lower(trim(:name)) AND instance_id = :instanceId`
- Berechtigungsprüfung: Rolle „Enterprise Architekt" oder äquivalente Schreibberechtigung auf Resource `catalog`

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft |
