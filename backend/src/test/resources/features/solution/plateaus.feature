# Spec: openspec solution/plateaus, solution/solution-creation, solution/solution-baseline (us-084-plateaus)
# Source material: requirements/user-stories/US-084, US-085, US-089, US-155, US-038 AC3/AC4; UC-11 (owner decisions 2026-11-17)

@US-084 @UC-11 @solution
Feature: Plateaus of plateau mode

  The first baseline plateau turns plateau mode on. Target and transition
  plateaus have a predecessor that is not realized and a basis, the current
  catalog or the content of the predecessor. Only the go-live changes the
  status, and a realized plateau is read-only. A solution may target a plateau
  and starts from its predecessor.

  Background:
    Given a running repository instance with a metamodel administrator
    And the instance has a baseline plateau

  # Requirement: Only one baseline plateau

  @AC3 @REQ-084
  Scenario: Second baseline
    When the architect creates the plateau "Second Baseline" with the status "baseline"
    Then the plateau answer is 422 naming "status"
    And the plateau answer has the detail "A baseline plateau already exists"
    And exactly one baseline plateau exists

  # Requirement: Create a target or transition plateau

  @AC2 @REQ-084
  Scenario: Target created
    When the architect creates the target plateau "Target 2027" succeeding "baseline" with the basis "catalog"
    Then the created plateau has the status "target", the predecessor "baseline" and the basis "catalog"

  @REQ-084
  Scenario: Transition created
    Given the target plateau "Target 2027" succeeds "baseline" with the basis "catalog"
    When the architect creates the transition plateau "Phase 1" succeeding "Target 2027"
    Then the plateau answer is 201

  # Requirement: Valid plateau fields

  @REQ-084
  Scenario Outline: Limits of name and description
    When the architect creates a target plateau with a name of <name> characters and a description of <description> characters
    Then the plateau answer is <status>

    Examples:
      | name | description | status |
      | 255  | 0           | 201    |
      | 256  | 0           | 400    |
      | 40   | 2000        | 201    |
      | 40   | 2001        | 400    |

  # Requirement: Predecessor of a plateau

  @REQ-084
  Scenario: Realized predecessor
    Given the plateau "Baseline 2025" is realized
    When the architect creates the target plateau "Target 2027" succeeding "Baseline 2025" with the basis "catalog"
    Then the plateau answer is 422 naming "succeeds"

  @REQ-084
  Scenario: Cycle
    Given the target plateau "Target 2027" succeeds "baseline" with the basis "catalog"
    And the target plateau "Phase 1" succeeds "Target 2027" with the basis "catalog"
    When the architect changes the predecessor of "Target 2027" to "Phase 1"
    Then the plateau answer is 422 naming "succeeds"
    And the plateau "Target 2027" has the predecessor "baseline"

  @REQ-084
  Scenario: Predecessor of a plateau in use
    Given the target plateau "Target 2027" succeeds "baseline" with the basis "catalog"
    And the target plateau "Other" succeeds "baseline" with the basis "catalog"
    And the solution "CRM Replacement" targets "Target 2027"
    When the architect changes the predecessor of "Target 2027" to "Other"
    Then the plateau answer is 409 of type "plateau-in-use"
    And the plateau "Target 2027" has the predecessor "baseline"

  # Requirement: Basis of a plateau, Audit of plateaus

  @US-155 @AC3
  Scenario: Basis changed
    Given the target plateau "Target 2028" succeeds "baseline" with the basis "predecessor"
    When the architect changes the basis of "Target 2028" to "catalog"
    Then the plateau answer is 200
    And the plateau "Target 2028" has the basis "catalog"
    And the change of "Target 2028" is audited as "plateau.updated" naming "basis"

  # Requirement: Edit a plateau, Realized plateau is read-only

  @REQ-084
  Scenario: Status changed by an edit
    Given the target plateau "Target 2027" succeeds "baseline" with the basis "catalog"
    When the architect sends the status "baseline" for "Target 2027"
    Then the plateau answer is 422 naming "status"

  @US-089 @AC1 @REQ-089
  Scenario: Realized plateau edited
    Given the plateau "Baseline 2025" is realized
    When the architect renames "Baseline 2025" to "Renamed"
    Then the plateau answer is 422
    And the plateau answer has the detail "Realized plateau is read-only"
    And the plateau "Baseline 2025" is still named "Baseline 2025"

  # Requirement: Read plateaus, Permissions for plateaus

  @US-085 @REQ-085
  Scenario: Plateaus listed
    Given the target plateau "Target 2027" succeeds "baseline" with the basis "catalog"
    When the architect lists the plateaus
    Then the baseline plateau comes first

  @REQ-084
  Scenario: Creation without plateau:write
    When the viewer creates the target plateau "Forbidden" succeeding "baseline"
    Then the plateau answer is 403

  @REQ-085
  Scenario: Reading without a permission
    When the viewer lists the plateaus
    Then the plateau answer is 403

  # Requirement: Create a solution in plateau mode, Both plateau references or none

  @US-038 @AC3 @REQ-038
  Scenario: Solution with plateaus
    Given the target plateau "Target 2027" succeeds "baseline" with the basis "catalog"
    When the architect creates the solution "Plateau Solution" from "baseline" to "Target 2027"
    Then the solution is created from "baseline" to "Target 2027"

  @US-038 @AC4 @REQ-038
  Scenario: Only the start
    When the architect creates the solution "Half Solution" from "baseline" to ""
    Then the plateau answer is 422
    And the plateau answer has the detail "Both plateau references must be set or both empty"

  @US-038 @AC3 @REQ-038
  Scenario: Start is not the predecessor
    Given the target plateau "Target 2027" succeeds "baseline" with the basis "catalog"
    And the target plateau "Phase 1" succeeds "Target 2027" with the basis "catalog"
    When the architect creates the solution "Wrong Start" from "baseline" to "Phase 1"
    Then the plateau answer is 422 naming "fromPlateauId"

  # Requirement: Content of a plateau, Baseline of a solution in plateau mode, Lifecycle of a baseline entry

  @US-155 @AC2
  Scenario: Basis predecessor
    Given the target plateau "Target 2027" succeeds "baseline" with the basis "catalog"
    And the target plateau "Target 2028" succeeds "Target 2027" with the basis "predecessor"
    And the solution "CRM Replacement" targets "Target 2027"
    And the solution "Analytics Platform" targets "Target 2028"
    And the landscape holds the entity "Legacy CRM"
    And the landscape holds the entity "ERP-Core"
    And "CRM Replacement" retires "Legacy CRM"
    And "CRM Replacement" proposes the new entity "Salesforce"
    When the architect loads the baseline of "Analytics Platform"
    Then "Legacy CRM" is "retiring" by "CRM Replacement"
    And "Salesforce" is "proposed" by "CRM Replacement"
    And "ERP-Core" is active in the baseline

  @US-155 @AC1
  Scenario: Basis catalog
    Given the target plateau "Target 2027" succeeds "baseline" with the basis "catalog"
    And the solution "CRM Replacement" targets "Target 2027"
    And the landscape holds the entity "Legacy CRM"
    And "CRM Replacement" proposes the new entity "Salesforce"
    When the architect loads the baseline of "CRM Replacement"
    Then "Legacy CRM" is active in the baseline
    And the baseline holds no "Salesforce"
