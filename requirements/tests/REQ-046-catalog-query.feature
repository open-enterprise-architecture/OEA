# Derived from: requirements/req/req-046-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-06
Feature: REQ-046 – Execute catalog query (live data)

  On demand, the system MUST execute a live query against the
  architecture repository for a
  [Catalog](../../business-objects/catalog.md) and return the entities
  of the `primaryEntityType` as a paginated list. For each entity, the
  values of the configured `columns` (attributes) as well as the
  results of all `joinDefinitions` MUST be included.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Happy path, aggregate mode – catalog "Application Inventory", primary...
    Given catalog "Application Inventory", primaryEntityType=ApplicationComponent, 2 columns (name, status), 1 join (DataFlow→Interface, aggregate); repository contains CRM-System (status=active) with 2 interfaces
    When  `GET /api/v1/catalogs/{id}/query`
    Then  response contains 1 row for CRM-System; `joinResults.Interfaces` is an array with 2 entries

  @AC2
  Scenario: AC2 – rows mode – as AC1
    Given as AC1
    When  `GET /api/v1/catalogs/{id}/query?joinMode[join-id]=rows`
    Then  response contains 2 rows (one per interface); `columns.name` = "CRM-System" in both rows

  @AC3
  Scenario: AC3 – Filter – repository contains 5 ApplicationComponents, of which 2 have status=active
    Given repository contains 5 ApplicationComponents, of which 2 have status=active
    When  `GET /api/v1/catalogs/{id}/query?filter[status][eq]=active`
    Then  `totalCount=2`; only the 2 active entities in the data array

  @AC4
  Scenario: AC4 – Entity without join results – ERP-Core (ApplicationComponent) has no DataFlow connections
    Given ERP-Core (ApplicationComponent) has no DataFlow connections
    When  query in aggregate mode
    Then  ERP-Core appears in the response with `joinResults.Interfaces = []`; is NOT excluded from the result set (LEFT JOIN semantics)

  @AC5
  Scenario: AC5 – Sorting and pagination – 60 ApplicationComponents
    Given 60 ApplicationComponents
    When  `GET /api/v1/catalogs/{id}/query?page=1&pageSize=20&sortBy=name&sortDir=asc`
    Then  `totalCount=60`; `data` contains entries 21–40 alphabetically by name

  @AC6
  Scenario: AC6 – Empty repository – primaryEntityType has no entities in the repository
    Given primaryEntityType has no entities in the repository
    When  query
    Then  HTTP 200 with `totalCount=0`, `data=[]`; no error
