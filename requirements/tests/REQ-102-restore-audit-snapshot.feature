# Derived from: requirements/req/req-102-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-15
Feature: REQ-102 – Audit snapshot before every restore

  Before every restore (full UC-15 or partial UC-16)
  the system MUST save the current entity state as an immutable
  EntityVersion snapshot. The new EntityVersion entry MUST
  contain `restoredFromVersion` (source version).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Snapshot entry after restore – a restore is executed
    Given the precondition according to the requirement is met
    When  a restore is executed
    Then  a new EntityVersion entry with `restoredFromVersion=N` is visible in the timeline

  @AC2
  Scenario: AC2 – Marker in timeline – the timeline is displayed after a restore
    Given the precondition according to the requirement is met
    When  the timeline is displayed after a restore
    Then  the new entry shows the marker "Restored from vN"

  @AC3
  Scenario: AC3 – Snapshot preserved before restore – the restore is completed
    Given the precondition according to the requirement is met
    When  the restore is completed
    Then  the state BEFORE the restore is present as an independent snapshot in `entity_versions`
