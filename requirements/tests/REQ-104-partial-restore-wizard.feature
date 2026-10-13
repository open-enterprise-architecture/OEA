# Derived from: requirements/req/req-104-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-16
Feature: REQ-104 – Partial restore three-step wizard

  The system MUST provide a three-step wizard for selective
  restore: step 1 General Properties
  (`name`, `description`, `isLogical`), step 2 Custom Properties
  (all configured properties from `snapshot.propertyDefinitions`),
  step 3 connection overview (informational:
  changed/added/removed). Each step MUST be individually
  skippable.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Source version prefilled – the wizard is started from the timeline (UC-14) for version v3
    Given the precondition according to the requirement is met
    When  the wizard is started from the timeline (UC-14) for version v3
    Then  source version v3 is prefilled

  @AC2
  Scenario: AC2 – Automatic skipping – step 1 contains no differences in General Properties
    Given the precondition according to the requirement is met
    When  step 1 contains no differences in General Properties
    Then  step 1 is skipped automatically with the note "No differences in General Properties"; wizard switches directly to step 2

  @AC3
  Scenario: AC3 – Confirm button on empty selection – no fields were selected in any step
    Given the precondition according to the requirement is met
    When  no fields were selected in any step
    Then  the confirm button is disabled (per REQ-106)
