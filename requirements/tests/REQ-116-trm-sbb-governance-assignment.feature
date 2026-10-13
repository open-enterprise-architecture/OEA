# Derived from: requirements/req/req-116-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-19
Feature: REQ-116 – SBB governance assignment per TRM category

  The system MUST allow, for every TRM category (also `scope=imported`),
  setting `preferredStandard` (single selection of an SBB),
  `acceptedAlternatives` (multi-select SBBs) and `deprecatedOptions`
  (multi-select SBBs). An SBB MUST NOT appear in the same category
  in more than one of the three lists (mutually
  exclusive).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Set preferredStandard and deviation note – `preferredStandard=PostgreSQL 17` is set in the category "Database Management"...
    Given the precondition according to the requirement is met
    When  `preferredStandard=PostgreSQL 17` is set in the category "Database Management"
    Then  all entities in this category with a different `instanceOfSBBId` show the note "Deviation from TRM standard"

  @AC2
  Scenario: AC2 – Mutually exclusive – the same SBB is to be set simultaneously in `preferredStandard` and `acceptedAlternativ...
    Given the precondition according to the requirement is met
    When  the same SBB is to be set simultaneously in `preferredStandard` and `acceptedAlternatives`
    Then  the system responds with HTTP 422 "SBB may appear in only one list"

  @AC3
  Scenario: AC3 – Imported category editable – a TRM category with `scope=imported` is opened
    Given the precondition according to the requirement is met
    When  a TRM category with `scope=imported` is opened
    Then  SBB assignments are editable; name and parent category are locked
