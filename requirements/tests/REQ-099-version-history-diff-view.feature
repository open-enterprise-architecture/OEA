# Derived from: requirements/req/req-099-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-14
Feature: REQ-099 – Version history field-wise diff

  When a version is clicked, the system MUST display a field-wise diff
  between this version and its successor
  (before/after values, highlighted in color: red=removed,
  green=added). Properties MUST be compared as individual fields
  (dot notation), not as a JSON blob.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Only changed fields – a version is clicked
    Given the precondition according to the requirement is met
    When  a version is clicked
    Then  the diff shows only the changed fields; identical fields are hidden

  @AC2
  Scenario: AC2 – Dot notation for properties – `properties.owner` was changed
    Given the precondition according to the requirement is met
    When  `properties.owner` was changed
    Then  the entry appears as "properties.owner: 'Kurt' → 'Michael'"

  @AC3
  Scenario: AC3 – Cumulative diff – version v2 and v7 are selected for comparison
    Given the precondition according to the requirement is met
    When  version v2 and v7 are selected for comparison
    Then  the system shows the cumulative diff over all 5 transitions (v2→v3, …, v6→v7)
