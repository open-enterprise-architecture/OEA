# Derived from: requirements/req/req-105-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-16
Feature: REQ-105 – Partial restore selective field selection

  In the wizard, exclusively fields that differ
  between source and current state MUST be selectable. Identical fields
  MUST be grayed out and not selectable.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Identical fields grayed out – a field is identical in source and current state
    Given the precondition according to the requirement is met
    When  a field is identical in source and current state
    Then  the checkbox is grayed out and not clickable

  @AC2
  Scenario: AC2 – New properties flagged – a property "cost-center" did not yet exist in the source version
    Given the precondition according to the requirement is met
    When  a property "cost-center" did not yet exist in the source version
    Then  the wizard shows "(not present in source version)"; checkbox not selectable

  @AC3
  Scenario: AC3 – Preview on field selection – the checkbox of a changed field is activated
    Given the precondition according to the requirement is met
    When  the checkbox of a changed field is activated
    Then  the value from the source version is displayed as a preview
