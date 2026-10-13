# Derived from: requirements/req/req-103-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-15
Feature: REQ-103 – Restore as atomic transaction

  Snapshot backup, entity update and writing of the new
  EntityVersion entry MUST occur in a single DB transaction on every restore (UC-15 and
  UC-16). On a technical
  error a complete rollback MUST occur; no
  inconsistent state (snapshot without update or update without snapshot)
  is permissible.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Rollback on DB error – a simulated DB error occurs after the snapshot is written
    Given the precondition according to the requirement is met
    When  a simulated DB error occurs after the snapshot is written
    Then  a complete rollback occurs; entity is unchanged; no snapshot in `entity_versions`

  @AC2
  Scenario: AC2 – Successful restore – the restore completes successfully
    Given the precondition according to the requirement is met
    When  the restore completes successfully
    Then  snapshot, entity update and version entry are all present and consistent
