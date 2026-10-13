# Derived from: requirements/req/req-120-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-20
Feature: REQ-120 – Technology diversity analysis with executive summary export

  The system MUST offer a technology diversity analysis that reports, per
  TRM category, the number of distinct SBBs actually in use
  (via `entity.instanceOfSBBId`). TRM categories with more
  than one SBB in use MUST be highlighted as "standardization potential".

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Highlight standardization potential – the TRM category "Container Orchestration" has 3 different SBBs in use...
    Given the precondition according to the requirement is met
    When  the TRM category "Container Orchestration" has 3 different SBBs in use
    Then  this category is highlighted as "standardization potential"

  @AC2
  Scenario: AC2 – List missing standards – TRM categories without a configured `preferredStandard` exist
    Given the precondition according to the requirement is met
    When  TRM categories without a configured `preferredStandard` exist
    Then  they appear in a separate list "Missing standards"

  @AC3
  Scenario: AC3 – PDF export with executive summary – the export as PDF is triggered
    Given the precondition according to the requirement is met
    When  the export as PDF is triggered
    Then  the document contains an executive summary section with top 3 risks (prohibited entities, gap rate, diversity hotspots) and detail tables per analysis section
