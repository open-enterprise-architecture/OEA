# Derived from: requirements/req/req-138-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: test (automated) + demonstration

@functional @must @UC-13
Feature: REQ-138 – Browser panel shows solutions as primary navigation level

  The browser panel in the native client MUST show solutions as primary
  navigation nodes at the top level. A virtual "Current State" node
  MUST appear as the first node, representing the
  aggregated contents of all realized solutions.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – At least one implemented solution exists
    Given at least one implemented solution exists
    When  browser panel is opened
    Then  "Current State" appears as the first node

  @AC2
  Scenario: AC2 – Solution with `fromPlateauId=P1, toPlateauId=P2`
    Given solution with `fromPlateauId=P1, toPlateauId=P2`
    When  browser panel is loaded
    Then  solution appears under plateau node (e.g. "CURRENT → TARGET 2027")

  @AC3
  Scenario: AC3 – Solution with `status=archived`
    Given solution with `status=archived`
    When  browser panel is displayed
    Then  archived solution is not visible
