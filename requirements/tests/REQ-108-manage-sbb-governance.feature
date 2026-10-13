# Derived from: requirements/req/req-108-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-17
Feature: REQ-108 – Manage SBB governance status

  The system MUST allow creating, editing and deleting
  `SolutionBuildingBlock` entries (SBBs) with `scope=organization`.
  Mandatory fields: `name`, `governanceStatus` (`[approved,
  acceptable, deprecated, prohibited]`).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Create SBB – an SBB is created with `governanceStatus=approved` and `implements=[ABB-UUID]`...
    Given the precondition according to the requirement is met
    When  an SBB is created with `governanceStatus=approved` and `implements=[ABB-UUID]`
    Then  the system responds with HTTP 201

  @AC2
  Scenario: AC2 – Prohibited warning on entities – an SBB is set to `governanceStatus=prohibited`
    Given the precondition according to the requirement is met
    When  an SBB is set to `governanceStatus=prohibited`
    Then  all entities with `instanceOfSBBId=this-SBB` show the note "Product in use prohibited – migration required" in the UI

  @AC3
  Scenario: AC3 – ABB reference warning – an SBB is created without `implements` ABBs
    Given the precondition according to the requirement is met
    When  an SBB is created without `implements` ABBs
    Then  the system shows a governance warning "SBB without ABB reference is only a product catalog entry" without blocking saving
