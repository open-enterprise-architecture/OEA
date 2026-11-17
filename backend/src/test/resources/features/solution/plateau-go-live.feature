# Spec: openspec solution/plateau-go-live, solution/solution-go-live (us-086-plateau-go-live)
# Source material: requirements/user-stories/US-086, US-087, US-088, US-157; UC-11 (owner decisions 2026-11-17)

@US-157 @UC-11 @solution
Feature: Take a plateau live

  The plateau that succeeds the baseline goes live with its active solutions in
  one transaction: their deltas reach the catalog, the plateau becomes the
  baseline and the old baseline is realized. The name of the plateau confirms
  the go-live, and warnings do not block it.

  Background:
    Given a running repository instance with a metamodel administrator
    And the plateau "Target Go-Live" succeeds the current baseline

  # Requirement: Preview of a plateau go-live

  @US-087 @AC1
  Scenario: Preview with a warning
    Given the solution "CRM Plan" with status "draft" targets "Target Go-Live"
    And the solution "ERP Plan" with status "approved" targets "Target Go-Live"
    When the architect reads the go-live preview of "Target Go-Live"
    Then the preview lists the solutions "CRM Plan" and "ERP Plan"
    And the preview warns that "CRM Plan" is not yet approved

  # Requirement: Confirmation by name

  @US-086 @AC2
  Scenario: Wrong name
    When the architect takes "Target Go-Live" live with the name "Wrong Name"
    Then the plateau answer is 422 naming "name"
    And "Target Go-Live" is still a target plateau

  # Requirements: Solutions go live with their plateau, Effects on plateaus

  @US-088 @AC2 @US-157 @AC1
  Scenario: Plateau and solutions go live
    Given the solution "CRM Plan" with status "approved" targets "Target Go-Live"
    And the landscape holds the entity "Legacy CRM"
    And "CRM Plan" retires "Legacy CRM"
    When the architect takes "Target Go-Live" live
    Then "Target Go-Live" is the baseline and the old baseline is realized
    And the solution "CRM Plan" is "implemented"
    And the plateau go-live deleted "Legacy CRM"

  # Requirement: Only the next plateau goes live

  @US-157
  Scenario: Later plateau
    Given the target plateau "Target After" succeeds "Target Go-Live" with the basis "catalog"
    When the architect takes "Target After" live
    Then the plateau answer is 409 of type "plateau-not-next"

  # Requirement: Permission for a plateau go-live

  @UC-11
  Scenario: Without plateau:go-live
    When the viewer takes "Target Go-Live" live
    Then the plateau answer is 403
    And "Target Go-Live" is still a target plateau

  # Requirement: A plateau solution goes live with its plateau

  @US-157 @AC3
  Scenario: Single go-live of a plateau solution
    Given the solution "CRM Plan" with status "approved" targets "Target Go-Live"
    When the architect takes the solution "CRM Plan" live on its own
    Then the plateau answer is 409 of type "plateau-solution"
    And the solution "CRM Plan" is "approved"
