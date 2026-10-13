# Derived from: requirements/req/req-140-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: test (automated)

@functional @must @UC-11 @UC-13
Feature: REQ-140 – Plateau-free solutions visible only in the Current State node

  Solutions in project mode (both plateau IDs `null`) MUST NOT
  appear as standalone top-level nodes in the browser. Their
  implemented contents MUST flow into the virtual "Current
  State" node.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Solution A (project mode, `status=implemented`), Solution B (plateau mode...
    Given Solution A (project mode, `status=implemented`), Solution B (plateau mode, CURRENT→TARGET)
    When  browser panel is opened
    Then  Solution A does NOT appear as its own node; Solution B appears under plateau node

  @AC2
  Scenario: AC2 – Solution A (project mode, `status=implemented`) with 3 components
    Given Solution A (project mode, `status=implemented`) with 3 components
    When  "Current State" is expanded in the browser
    Then  the 3 components are visible under "Current State → Components"

  @AC3
  Scenario: AC3 – Solution C (project mode, `status=in-progress`)
    Given Solution C (project mode, `status=in-progress`)
    When  browser panel is displayed
    Then  Solution C is visible neither in "Current State" nor anywhere else in the browser
