# Derived from: requirements/req/req-109-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-17
Feature: REQ-109 – Create and manage architecture pattern

  The system MUST allow creating, editing and deleting
  `ArchitecturePattern` entries. Mandatory fields: `name`,
  `problem` (Markdown), `solution` (Markdown).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Create pattern – a pattern is created with `problem` and `solution`
    Given the precondition according to the requirement is met
    When  a pattern is created with `problem` and `solution`
    Then  the system responds with HTTP 201; the pattern is visible in the library

  @AC2
  Scenario: AC2 – Mandatory field problem – a pattern is to be saved without the `problem` field
    Given the precondition according to the requirement is met
    When  a pattern is to be saved without the `problem` field
    Then  the system responds with HTTP 422 "problem is a mandatory field"

  @AC3
  Scenario: AC3 – Link relatedPatterns – `relatedPatterns` are linked to other patterns via multi-select
    Given the precondition according to the requirement is met
    When  `relatedPatterns` are linked to other patterns via multi-select
    Then  the links are saved and displayed in the detail view
