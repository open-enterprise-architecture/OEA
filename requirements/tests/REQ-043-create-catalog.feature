# Ableitung aus: requirements/req/req-043-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-06
Feature: REQ-043 – Katalog anlegen

  Das System MUSS dem Katalog-Manager ermöglichen, einen neuen
  [Catalog](../../business-objects/catalog.md) mit folgenden Feldern
  anzulegen: `name` (Pflicht, eindeutig innerhalb der Instanz),
  `description` (optional), `primaryEntityType` (Pflicht, muss ein
  gültiger EntityType-Name aus der MetamodelConfiguration sein),
  `scope` (Pflicht: `instance` | `personal`) und `defaultJoinMode`
  (Pflicht: `rows` | `aggregate`, Default: `aggregate`). Der Catalog
  wird initial ohne Spalten, Joins, Filter oder Views erstellt;
  Spalten- und Join-Konfiguration erfolgt in separaten Schritten
  (REQ-044, REQ-045).

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Anlegen, Happy Path – Kurt ist eingeloggt mit Rolle „Enterprise Architekt"; die MetamodelConfi...
    Given Kurt ist eingeloggt mit Rolle „Enterprise Architekt"; die MetamodelConfiguration enthält den EntityType `ApplicationComponent`
    When  `POST /api/v1/catalogs` mit `{ name: "Application Inventory", primaryEntityType: "ApplicationComponent", scope: "instance", defaultJoinMode: "aggregate" }`
    Then  HTTP 201; Response enthält id, name, primaryEntityType, scope, defaultJoinMode, createdBy=Kurts ID, leere columns/joinDefinitions/savedFilters/savedViews-Listen

  @AC2
  Scenario: AC2 – Name-Kollision – Ein Catalog mit name="Application Inventory" existiert bereits
    Given Ein Catalog mit name="Application Inventory" existiert bereits
    When  Erneuter POST mit demselben Name (egal ob identische Schreibweise oder Variationen wie Leerzeichen vorn/hinten)
    Then  HTTP 422 mit Fehlermeldung „Ein Katalog mit diesem Namen existiert bereits"

  @AC3
  Scenario: AC3 – Ungültiger EntityType – Die MetamodelConfiguration enthält keinen EntityType „UnbekannterTyp"
    Given Die MetamodelConfiguration enthält keinen EntityType „UnbekannterTyp"
    When  POST mit `primaryEntityType: "UnbekannterTyp"`
    Then  HTTP 422 „Der EntityType ‹UnbekannterTyp› ist in der MetamodelConfiguration nicht definiert"

  @AC4
  Scenario: AC4 – Scope=personal – Sabine legt einen Catalog mit scope=personal an
    Given Sabine legt einen Catalog mit scope=personal an
    When  Franz (anderer Nutzer) die Catalog-Übersicht abruft
    Then  Sabines personal-Catalog erscheint NICHT in Franzens Sicht; 404 bei direktem Zugriff

  @AC5
  Scenario: AC5 – Fehlende Berechtigung – Franz hat nur die Rolle „Betrachter"
    Given Franz hat nur die Rolle „Betrachter"
    When  Franz versucht, einen Catalog anzulegen
    Then  HTTP 403 Forbidden
