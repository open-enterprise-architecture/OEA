# Derived from: requirements/req/req-106-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-16
Feature: REQ-106 – Partial restore restoredFields log

  On every partial restore, the new EntityVersion entry MUST contain
  the exact list of restored fields in dot notation as
  `restoredFields` (e.g. `["description",
  "properties.owner"]`).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – restoredFields in version entry – a partial restore is executed with `description` and `properties.owner`...
    Given the precondition according to the requirement is met
    When  a partial restore is executed with `description` and `properties.owner`
    Then  the new EntityVersion entry contains `restoredFields=["description","properties.owner"]`

  @AC2
  Scenario: AC2 – Reject empty selection – no fields were selected and the confirm button is clicked
    Given the precondition according to the requirement is met
    When  no fields were selected and the confirm button is clicked
    Then  the button is disabled; an API call with empty `restoredFields` is rejected with HTTP 422

  @AC3
  Scenario: AC3 – Timeline readability – the timeline is displayed after a partial restore
    Given the precondition according to the requirement is met
    When  the timeline is displayed after a partial restore
    Then  the entry shows "Partially restored from v4 (description, properties.owner)"
