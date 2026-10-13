# Derived from: requirements/req/req-087-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-11
Feature: REQ-087 – Go-live without enforcement through warnings

  Go-live MUST NOT be blocked by non-implemented solutions or open
  CEL rule violations. These states MUST be displayed as
  warnings in the confirmation dialog (count + list),
  but do not block execution.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Non-implemented solutions as warning – 3 non-implemented solutions exist at go-live
    Given the precondition according to the requirement is met
    When  3 non-implemented solutions exist at go-live
    Then  the dialog shows the warning "3 solutions not implemented"; go-live remains executable

  @AC2
  Scenario: AC2 – CEL violations as warning – CEL rule violations exist at go-live
    Given the precondition according to the requirement is met
    When  CEL rule violations exist at go-live
    Then  the dialog shows the warning "N business rule violations"; go-live remains executable

  @AC3
  Scenario: AC3 – No warning section without warnings – at go-live neither non-implemented solutions nor CEL violations exist...
    Given the precondition according to the requirement is met
    When  at go-live neither non-implemented solutions nor CEL violations exist
    Then  no warning section is displayed in the dialog
