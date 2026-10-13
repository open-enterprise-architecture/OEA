# Derived from: requirements/req/req-086-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-11
Feature: REQ-086 – Go-live two-step confirmation

  Before executing a go-live transition, the system MUST display a
  confirmation dialog that (1) summarizes the diff
  (P1→baseline, P0→realized), (2) lists non-implemented solutions and
  open CEL violations as warnings and (3) requires the manual
  entry of the exact plateau name as a second confirmation step.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Button disabled without name entry – the confirmation dialog is displayed without name entry
    Given the precondition according to the requirement is met
    When  the confirmation dialog is displayed without name entry
    Then  the "Confirm go-live" button is disabled

  @AC2
  Scenario: AC2 – Wrong name – an incorrect plateau name is entered
    Given the precondition according to the requirement is met
    When  an incorrect plateau name is entered
    Then  the button stays disabled; no error message is required

  @AC3
  Scenario: AC3 – Correct name – the exact plateau name is entered
    Given the precondition according to the requirement is met
    When  the exact plateau name is entered
    Then  the button is enabled and go-live can be executed
