# Derived from: requirements/req/req-100-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-14
Feature: REQ-100 – Historical entity state as immutable snapshot

  The system MUST make the complete entity state at every
  historical version retrievable (from
  `entity_versions.snapshot`). The complete state MUST be
  expandable in the UI ("Show complete state vN") and all fields
  incl.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Complete state expandable – "Show complete state v4" is clicked in the timeline
    Given the precondition according to the requirement is met
    When  "Show complete state v4" is clicked in the timeline
    Then  all fields of the snapshot, including empty properties, are displayed

  @AC2
  Scenario: AC2 – Deletion rejected – a DELETE request is sent to `/entity-versions/{id}`
    Given the precondition according to the requirement is met
    When  a DELETE request is sent to `/entity-versions/{id}`
    Then  the API responds with HTTP 405 Method Not Allowed

  @AC3
  Scenario: AC3 – Editing rejected – a PUT request is sent to `/entity-versions/{id}`
    Given the precondition according to the requirement is met
    When  a PUT request is sent to `/entity-versions/{id}`
    Then  the API responds with HTTP 405 Method Not Allowed
