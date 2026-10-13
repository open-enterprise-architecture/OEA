# Derived from: requirements/req/req-039-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @must @UC-05
Feature: REQ-039 – Show current IT landscape as solution baseline

  Within a solution, the system MUST display the current state of the
  IT landscape as a searchable and filterable baseline; in **project
  mode**, this state MUST be computed from the aggregation of all
  entities of solutions with status=`implemented`, in **plateau mode**
  from the entities of the solution's `fromPlateau`.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – two implemented solutions exist; solution A has entity `CRM-Legacy` (...
    Given two implemented solutions exist; solution A has entity `CRM-Legacy` (new), solution B has entity `ERP-Core` (new)
    When  Michael opens a new solution (project mode) and loads the baseline
    Then  `CRM-Legacy` and `ERP-Core` are shown in the list; no other entities

  @AC2
  Scenario: AC2 – solution A (implemented) has entity `CRM-Legacy` (modified: name chan...
    Given solution A (implemented) has entity `CRM-Legacy` (modified: name changed to "CRM-Legacy-v2"); solution B (implemented, more recent) has modified the same entity again
    When  the baseline is computed
    Then  it shows the state from solution B (more recent implementation wins)

  @AC3
  Scenario: AC3 – solution A (implemented) has marked entity `CRM-Legacy` as retiring
    Given solution A (implemented) has marked entity `CRM-Legacy` as retiring
    When  the baseline is computed
    Then  `CRM-Legacy` is **not** in the baseline (retiring from an implemented solution removes the entity)

  @AC4
  Scenario: AC4 – plateau P0 (baseline) with entities `ERP-Core` and `CRM-Legacy`; solu...
    Given plateau P0 (baseline) with entities `ERP-Core` and `CRM-Legacy`; solution with `fromPlateauId=P0` (plateau mode)
    When  Michael loads the baseline
    Then  exactly the entities of P0 are shown (including inherited entities, if any)

  @AC5
  Scenario: AC5 – no implemented solutions present (empty landscape, project mode)
    Given no implemented solutions present (empty landscape, project mode)
    When  Michael loads the baseline
    Then  the notice "No realized entities available" appears; `modified` and `retiring` options are disabled

  @AC6
  Scenario: AC6 – baseline with 50 entities of different types
    Given baseline with 50 entities of different types
    When  Michael filters by `ApplicationComponent`
    Then  only entities of type `ApplicationComponent` are shown
