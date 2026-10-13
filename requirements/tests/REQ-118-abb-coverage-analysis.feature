# Derived from: requirements/req/req-118-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-20
Feature: REQ-118 – ABB coverage analysis (gap rate)

  The system MUST offer an ABB coverage analysis that reports, for every
  ABB with `governanceStatus=approved`: number of conforming
  entities (`conformsToABBId`), number of implementing SBBs and whether
  a gap exists (no conforming entities). The overall rate (ABBs
  with at least one instance / all approved ABBs) MUST be visible as a
  percentage "gap rate".

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Gap rate calculation – 5 approved ABBs exist, 2 of them without an instance
    Given the precondition according to the requirement is met
    When  5 approved ABBs exist, 2 of them without an instance
    Then  the analysis shows gap rate = 40%; the table shows all 5 ABBs with their instance counts

  @AC2
  Scenario: AC2 – ABB detail view – an ABB is opened in the analysis table
    Given the precondition according to the requirement is met
    When  an ABB is opened in the analysis table
    Then  implementing SBBs and conforming entities are visible as clickable links

  @AC3
  Scenario: AC3 – Empty state – no ABBs are configured yet
    Given the precondition according to the requirement is met
    When  no ABBs are configured yet
    Then  the system shows the note "No ABBs configured yet; start with UC-17"
