# Derived from: requirements/req/req-110-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-17
Feature: REQ-110 – Compose reference architecture from building blocks

  The system MUST allow creating, editing and deleting
  `ReferenceArchitecture` entries. Mandatory fields: `name`,
  `continuumLevel`, `governanceStatus`.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Create reference architecture and make available as blueprint – a reference architecture is created with 3 ABBs and 1 pattern and `gover...
    Given the precondition according to the requirement is met
    When  a reference architecture is created with 3 ABBs and 1 pattern and `governanceStatus=approved` is set
    Then  the system responds with HTTP 201; the reference architecture is selectable as a blueprint in UC-11 (plateau creation)

  @AC2
  Scenario: AC2 – Empty composition warning – a reference architecture is to be saved without ABBs and without patterns...
    Given the precondition according to the requirement is met
    When  a reference architecture is to be saved without ABBs and without patterns
    Then  the system shows a governance warning; saving is not blocked

  @AC3
  Scenario: AC3 – Mandatory field targetIndustry – a reference architecture with `continuumLevel=industry` is to be saved without `targetIn...
    Given the precondition according to the requirement is met
    When  a reference architecture with `continuumLevel=industry` is to be saved without `targetIndustry`
    Then  the system responds with HTTP 422
