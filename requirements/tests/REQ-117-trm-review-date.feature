# Derived from: requirements/req/req-117-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-19
Feature: REQ-117 – Set TRM category review date automatically and filter

  The system MUST set `lastReviewedAt` automatically to the current UTC date
  every time a TRM category entry is saved.
  The system MUST offer a filter "Last reviewed more than N months ago"
  (parameterizable, default: 12 months) in the TRM overview
  that highlights categories with an outdated or missing review date.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Automatic lastReviewedAt – a TRM category is saved
    Given the precondition according to the requirement is met
    When  a TRM category is saved
    Then  `lastReviewedAt` is set to the current UTC date

  @AC2
  Scenario: AC2 – Filter by review date – the filter "Last reviewed more than 12 months ago" is activated in the TRM overview...
    Given the precondition according to the requirement is met
    When  the filter "Last reviewed more than 12 months ago" is activated in the TRM overview
    Then  categories with `lastReviewedAt` < today − 12 months or `lastReviewedAt = null` are highlighted
