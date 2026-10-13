# Derived from: requirements/req/req-112-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-18
Feature: REQ-112 – Activate embedded continuum packages

  The system MUST offer a list of embedded continuum packages
  (at least: "TOGAF 10 – Technical Reference Model"). Each
  package MUST show a preview before import: number of ABBs, SBBs,
  patterns, reference architectures and TRM categories as well as the
  most important category names.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Package preview – the package list is opened
    Given the precondition according to the requirement is met
    When  the package list is opened
    Then  "TOGAF 10 TRM" is visible with preview (47 ABBs, 38 TRM categories, 12 patterns)

  @AC2
  Scenario: AC2 – Import by click – "Import" is clicked
    Given the precondition according to the requirement is met
    When  "Import" is clicked
    Then  the import is executed atomically; an import log shows the result

  @AC3
  Scenario: AC3 – Already imported package – a package has already been imported
    Given the precondition according to the requirement is met
    When  a package has already been imported
    Then  the UI shows a badge "Already imported" and a note on conflict handling
