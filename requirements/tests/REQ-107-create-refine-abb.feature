# Derived from: requirements/req/req-107-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-17
Feature: REQ-107 – Create ABB and manage refinement hierarchy

  The system MUST allow creating, editing and deleting
  `ArchitectureBuildingBlock` entries (ABBs) with `scope=organization`.
  Mandatory fields: `name` (unique per `continuumLevel`),
  `continuumLevel`, `maturityLevel`, `governanceStatus`.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Create ABB – an ABB is created with `continuumLevel=organization` and `maturityLevel=established...
    Given the precondition according to the requirement is met
    When  an ABB is created with `continuumLevel=organization` and `maturityLevel=established`
    Then  the system responds with HTTP 201; the ABB is visible in the library

  @AC2
  Scenario: AC2 – Cycle detection – ABB "B" is set to `refines=A` and subsequently ABB "A" is to be set to `refine...
    Given the precondition according to the requirement is met
    When  ABB "B" is set to `refines=A` and subsequently ABB "A" is to be set to `refines=B`
    Then  the system responds with HTTP 422 "Cycle detected: A → B → A" and rejects saving

  @AC3
  Scenario: AC3 – Mandatory field industry – an ABB with `continuumLevel=industry` is to be saved without the `industry` field...
    Given the precondition according to the requirement is met
    When  an ABB with `continuumLevel=industry` is to be saved without the `industry` field
    Then  the system responds with HTTP 422
