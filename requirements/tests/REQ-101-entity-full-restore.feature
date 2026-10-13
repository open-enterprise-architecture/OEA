# Derived from: requirements/req/req-101-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-15
Feature: REQ-101 – Full restore of an entity to an earlier state

  The system MUST allow restoring an ArchitectureEntity to
  any earlier state. Before execution, a confirmation dialog MUST be
  displayed that shows the diff (current vs.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Restore to earlier state – an entity is restored to version v3
    Given the precondition according to the requirement is met
    When  an entity is restored to version v3
    Then  `name`, `description` and `properties` are set to v3 values; `id` is unchanged

  @AC2
  Scenario: AC2 – Confirmation dialog with diff – the restore is initiated
    Given the precondition according to the requirement is met
    When  the restore is initiated
    Then  the confirmation dialog shows which fields differ between the current state and the target version

  @AC3
  Scenario: AC3 – Cancel without change – the dialog is cancelled
    Given the precondition according to the requirement is met
    When  the dialog is cancelled
    Then  the entity remains unchanged
