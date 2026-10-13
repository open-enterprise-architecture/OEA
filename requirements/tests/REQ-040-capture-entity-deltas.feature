# Derived from: requirements/req/req-040-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @must @UC-05
Feature: REQ-040 – Capture EntityDeltas of a solution

  The system MUST enable the solution architect to capture, edit, and
  remove EntityDeltas of the types `new`, `modified`, and `retiring`
  within a solution; solutions with status=`implemented` MUST NOT be
  modifiable (BR-03). New entities (`deltaType=new`) MUST be creatable
  both via the **catalog** (form) and via the **diagram** (canvas); in
  the diagram, a suggestion list of existing components from the
  current IT landscape MUST be displayed when entering the name.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Michael is working on a solution in status `draft`; entity `CRM-Legac...
    Given Michael is working on a solution in status `draft`; entity `CRM-Legacy` (ApplicationComponent) is in the baseline
    When  he marks `CRM-Legacy` as `retiring`
    Then  `CRM-Legacy` appears in the solution's delta list as `deltaType=retiring`; diff view (REQ-041) shows it as "decommissioned"

  @AC2
  Scenario: AC2 – same initial situation as AC1
    Given same initial situation as AC1
    When  Michael creates a new entity `Salesforce` of type `ApplicationComponent` with `deltaType=new`
    Then  `Salesforce` appears in the delta list as `deltaType=new`; `Salesforce` is **not** yet in the baseline (added new by this solution)

  @AC3
  Scenario: AC3 – entity `ERP-Core` (ApplicationComponent) is in the baseline
    Given entity `ERP-Core` (ApplicationComponent) is in the baseline
    When  Michael marks `ERP-Core` as `modified` and changes the version from v4 to v5
    Then  the delta contains `changes = {version: {before: "v4", after: "v5"}}`; diff view shows the change

  @AC4
  Scenario: AC4 – solution has status=`implemented`
    Given solution has status=`implemented`
    When  Michael tries to add a new delta
    Then  409 Conflict with message "This solution is completed and can no longer be modified"

  @AC5
  Scenario: AC5 – `CRM-Legacy` already has a `retiring` delta in this solution
    Given `CRM-Legacy` already has a `retiring` delta in this solution
    When  Michael tries to add `CRM-Legacy` again as `modified`
    Then  validation error "A delta already exists for CRM-Legacy in this solution"

  @AC6
  Scenario: AC6 – Michael enters as `modified` delta an entityId that is not in the bas...
    Given Michael enters as `modified` delta an entityId that is not in the baseline
    When  he saves
    Then  validation error "Entity not found in the current landscape"
