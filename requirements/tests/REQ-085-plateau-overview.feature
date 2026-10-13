# Derived from: requirements/req/req-085-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-11
Feature: REQ-085 – Plateau overview grouped by status

  The system MUST display plateaus in a structured overview grouped by
  status: current baseline (highlighted),
  target plateaus (with planned `validFrom`), realized plateaus
  (collapsible, chronologically descending). Each plateau shows name,
  status, `validFrom`/`validTo` and the number of linked solutions.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Grouped overview – the plateau overview is opened and baseline, target and realized plateaus exist...
    Given the precondition according to the requirement is met
    When  the plateau overview is opened and baseline, target and realized plateaus exist
    Then  the baseline is highlighted, targets are shown with `validFrom` and realized plateaus are displayed as a collapsible section

  @AC2
  Scenario: AC2 – Empty target section – no target plateaus exist
    Given the precondition according to the requirement is met
    When  no target plateaus exist
    Then  the target section is displayed with the note "No target plateau planned yet"
