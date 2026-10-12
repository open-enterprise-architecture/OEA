# Ableitung aus: requirements/req/req-046-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-06
Feature: REQ-046 – Katalog-Abfrage ausführen (Live-Daten)

  Das System MUSS für einen
  [Catalog](../../business-objects/catalog.md) auf Abruf eine
  Live-Abfrage gegen das Architecture-Repository ausführen und die
  Entitäten des `primaryEntityType` als paginierte Liste zurückliefern.
  Für jede Entität MÜSSEN die Werte der konfigurierten `columns`
  (Attribute) sowie die Ergebnisse aller `joinDefinitions` enthalten
  sein.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Happy Path, aggregate-Modus – Catalog „Application Inventory", primaryEntityType=ApplicationComponent,...
    Given Catalog „Application Inventory", primaryEntityType=ApplicationComponent, 2 Spalten (name, status), 1 Join (DataFlow→Interface, aggregate); Repository enthält CRM-System (status=active) mit 2 Interfaces
    When  `GET /api/v1/catalogs/{id}/query`
    Then  Response enthält 1 Zeile für CRM-System; `joinResults.Schnittstellen` ist Array mit 2 Einträgen

  @AC2
  Scenario: AC2 – rows-Modus – wie AC1
    Given wie AC1
    When  `GET /api/v1/catalogs/{id}/query?joinMode[join-id]=rows`
    Then  Response enthält 2 Zeilen (je Interface eine); `columns.name` = „CRM-System" in beiden Zeilen

  @AC3
  Scenario: AC3 – Filter – Repository enthält 5 ApplicationComponents, davon 2 mit status=active
    Given Repository enthält 5 ApplicationComponents, davon 2 mit status=active
    When  `GET /api/v1/catalogs/{id}/query?filter[status][eq]=active`
    Then  `totalCount=2`; nur die 2 aktiven Entitäten im data-Array

  @AC4
  Scenario: AC4 – Entität ohne Join-Ergebnisse – ERP-Core (ApplicationComponent) hat keine DataFlow-Connections
    Given ERP-Core (ApplicationComponent) hat keine DataFlow-Connections
    When  Abfrage im aggregate-Modus
    Then  ERP-Core erscheint in der Antwort mit `joinResults.Schnittstellen = []`; wird NICHT aus der Ergebnismenge ausgeschlossen (LEFT-JOIN-Semantik)

  @AC5
  Scenario: AC5 – Sortierung und Paginierung – 60 ApplicationComponents
    Given 60 ApplicationComponents
    When  `GET /api/v1/catalogs/{id}/query?page=1&pageSize=20&sortBy=name&sortDir=asc`
    Then  `totalCount=60`; `data` enthält Einträge 21–40 alphabetisch nach Name

  @AC6
  Scenario: AC6 – Leeres Repository – primaryEntityType hat keine Entitäten im Repository
    Given primaryEntityType hat keine Entitäten im Repository
    When  Abfrage
    Then  HTTP 200 mit `totalCount=0`, `data=[]`; kein Fehler
