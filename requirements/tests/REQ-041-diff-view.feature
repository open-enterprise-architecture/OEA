# Derived from: requirements/req/req-041-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated) + exploration (UI test)

@functional @should @UC-05
Feature: REQ-041 – Diff view of current state vs. target state of a solution

  Within a solution, the system SHOULD provide a diff view that
  contrasts the current state of the affected entities (baseline from
  REQ-039) with the target state after applying all EntityDeltas
  (REQ-040) and visually highlights the differences; the diff view
  SHOULD be updated after each delta change and MUST NOT be persisted
  separately.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – solution has a `new` delta for `Salesforce (ApplicationComponent)` an...
    Given solution has a `new` delta for `Salesforce (ApplicationComponent)` and a `retiring` delta for `CRM-Legacy`
    When  Michael opens the diff view
    Then  exactly two entries appear; `Salesforce` marked green (no entry on the left), `CRM-Legacy` red/struck through on the right

  @AC2
  Scenario: AC2 – solution has a `modified` delta for `ERP-Core` (version: v4 → v5)
    Given solution has a `modified` delta for `ERP-Core` (version: v4 → v5)
    When  Michael opens the diff view
    Then  `ERP-Core` appears once; left side shows `version=v4`, right side `version=v5`; only the changed property is highlighted

  @AC3
  Scenario: AC3 – Michael adds another delta (after opening the diff view)
    Given Michael adds another delta (after opening the diff view)
    When  he saves the delta
    Then  the diff view updates without a full reload and shows the new entity

  @AC4
  Scenario: AC4 – solution has no deltas
    Given solution has no deltas
    When  Michael opens the diff view
    Then  the notice "No changes captured yet" appears; no error

  @AC5
  Scenario: AC5 – solution has 20 deltas of different types
    Given solution has 20 deltas of different types
    When  Michael opens the diff view
    Then  all 20 deltas are shown; entities without a delta are **not** visible (no landscape dump)
