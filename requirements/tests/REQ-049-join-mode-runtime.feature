# Derived from: requirements/req/req-049-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-06
Feature: REQ-049 – Switch the join mode of a catalog at runtime

  The system MUST enable all catalog visitors to change the join mode
  (`rows` | `aggregate`) per JoinDefinition at runtime without
  modifying the stored catalog configuration. The table MUST be
  updated after switching without a page reload.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – rows mode via parameter – catalog "Application Inventory" with JoinDe...
    Given catalog "Application Inventory" with JoinDefinition id=join-1 (DataFlow→Interface), defaultJoinMode=aggregate; CRM-System has 2 interfaces
    When  `GET /api/v1/catalogs/{id}/query?joinMode[join-1]=rows`
    Then  response contains 2 rows for CRM-System (one per interface); Catalog.joinDefinitions[0].defaultJoinMode remains `aggregate` (unchanged)

  @AC2
  Scenario: AC2 – aggregate mode, stored default applies – as AC1
    Given as AC1
    When  `GET /api/v1/catalogs/{id}/query` (without joinMode parameter)
    Then  response contains 1 row for CRM-System with joinResults.Interfaces as an array

  @AC3
  Scenario: AC3 – No mutation on catalog – after AC1: `GET /api/v1/catalogs/{id}` (retrieve catalog configuration)
    Given the precondition according to the requirement is met
    When the described action is performed
    Then  `joinDefinitions[0].defaultJoinMode = "aggregate"` (unchanged)

  @AC4
  Scenario: AC4 – Unknown joinDefinitionId – `joinMode[nonexistent-id]=rows`
    Given the precondition according to the requirement is met
    When  `joinMode[nonexistent-id]=rows`
    Then  HTTP 400 "JoinDefinition ‹nonexistent-id› not found"

  @AC5
  Scenario: AC5 – Override multiple joins at once – catalog has 2 JoinDefinitions (join...
    Given catalog has 2 JoinDefinitions (join-1 defaultMode=aggregate, join-2 defaultMode=aggregate)
    When  `?joinMode[join-1]=rows&joinMode[join-2]=aggregate`
    Then  join-1 returns row expansion; join-2 remains aggregated
