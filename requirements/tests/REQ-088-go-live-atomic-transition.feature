# Derived from: requirements/req/req-088-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-11
Feature: REQ-088 – Go-live as atomic DB transaction

  The go-live transition MUST be executed as an atomic DB transaction
  and comprise the following changes: (1) P1: `status→baseline`,
  `validFrom=now(UTC)`; (2) P0: `status→realized`,
  `validTo=now(UTC)`, read-only; (3) all entities in P1 with
  `lifecycle_state=retiring` → `lifecycle_state=retired`. On a
  technical error a complete rollback MUST occur; no
  inconsistent state is permissible.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Rollback on DB error – a simulated DB error occurs after the P1 update
    Given the precondition according to the requirement is met
    When  a simulated DB error occurs after the P1 update
    Then  a complete rollback occurs; P0 and P1 are unchanged

  @AC2
  Scenario: AC2 – Successful go-live – go-live is executed successfully
    Given the precondition according to the requirement is met
    When  go-live is executed successfully
    Then  P1 has `status=baseline`, P0 has `status=realized`; all `retiring` entities in P1 have `lifecycle_state=retired`; all states are consistent
