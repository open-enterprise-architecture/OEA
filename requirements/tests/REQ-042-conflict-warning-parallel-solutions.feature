# Derived from: requirements/req/req-042-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-05
Feature: REQ-042 – Conflict warning for parallel EntityDeltas

  When capturing or updating an EntityDelta, the system SHOULD check
  whether another active solution (status ∉ {`implemented`,
  `archived`}) already contains an EntityDelta for the same entity; if
  so, a non-blocking warning with the name and owner of the
  conflicting solution SHOULD be displayed.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – solution A (draft, owner: Maria) contains a delta for entity `CRM-Leg...
    Given solution A (draft, owner: Maria) contains a delta for entity `CRM-Legacy`
    When  Michael creates a delta for `CRM-Legacy` in another solution B
    Then  a warning "This entity is also being changed in solution 'Solution A' (owner: Maria)" appears; the delta in solution B is saved anyway

  @AC2
  Scenario: AC2 – solution A (implemented) contains a delta for entity `CRM-Legacy`
    Given solution A (implemented) contains a delta for entity `CRM-Legacy`
    When  Michael creates a delta for `CRM-Legacy` in solution B
    Then  **no** warning (implemented is not an active conflict)

  @AC3
  Scenario: AC3 – solution A (archived) contains a delta for entity `CRM-Legacy`
    Given solution A (archived) contains a delta for entity `CRM-Legacy`
    When  Michael creates a delta for `CRM-Legacy` in solution B
    Then  **no** warning (archived is not an active conflict)

  @AC4
  Scenario: AC4 – two other solutions (A and B) each have a delta for `CRM-Legacy`
    Given two other solutions (A and B) each have a delta for `CRM-Legacy`
    When  Michael creates a delta for `CRM-Legacy` in solution C
    Then  two warnings appear (one per conflicting solution)

  @AC5
  Scenario: AC5 – warning was displayed for `CRM-Legacy` in solution B
    Given warning was displayed for `CRM-Legacy` in solution B
    When  Michael reloads solution B or opens the delta list
    Then  the warning is still visible (not a one-time toast, persistent display on the delta)

  @AC6
  Scenario: AC6 – solution A is set to `implemented` (go-live)
    Given solution A is set to `implemented` (go-live)
    When  Michael opens solution B with the delta for `CRM-Legacy`
    Then  the warning disappears (conflict resolved by go-live of A)
