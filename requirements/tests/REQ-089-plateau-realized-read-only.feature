# Derived from: requirements/req/req-089-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-11
Feature: REQ-089 – Realized plateau read-only

  Plateaus with `status=realized` MUST be locked for all write operations
  (read-only). Attempts to change name, description, `validFrom`
  or linked solutions of a realized plateau MUST be rejected
  with HTTP 422.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – API write protection – a PUT request is sent to `/plateaus/{id}` for a realized plateau
    Given the precondition according to the requirement is met
    When  a PUT request is sent to `/plateaus/{id}` for a realized plateau
    Then  the API responds with HTTP 422 "Realized plateau is read-only"

  @AC2
  Scenario: AC2 – UI write protection – a realized plateau is displayed in the overview
    Given the precondition according to the requirement is met
    When  a realized plateau is displayed in the overview
    Then  no edit button is visible
