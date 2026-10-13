# Derived from: requirements/req/req-092-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-12
Feature: REQ-092 – System-defined viewpoints read-only

  Viewpoints with `viewpointType=system-defined` MUST be locked for all
  write operations. In the UI, edit and delete actions MUST NOT be
  available for system-defined viewpoints.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – API write protection – a DELETE request is sent to `/viewpoints/{system-defined-id}`
    Given the precondition according to the requirement is met
    When  a DELETE request is sent to `/viewpoints/{system-defined-id}`
    Then  the API responds with HTTP 422

  @AC2
  Scenario: AC2 – UI write protection – system-defined viewpoints are displayed in the list
    Given the precondition according to the requirement is met
    When  system-defined viewpoints are displayed in the list
    Then  edit and delete buttons are not visible

  @AC3
  Scenario: AC3 – Import write protection – system-defined viewpoints are contained in the import (REQ-059)
    Given the precondition according to the requirement is met
    When  system-defined viewpoints are contained in the import (REQ-059)
    Then  they are not overwritten
