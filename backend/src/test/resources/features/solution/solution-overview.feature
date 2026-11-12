# Spec: openspec solution/solution-overview
# Source material: requirements/user-stories/US-038-create-solution.md; REQ-038 Outputs

@US-038 @UC-05 @solution
Feature: Read solutions

  Everyone who reads the repository sees the solutions and their
  architecture vision.

  Background:
    Given a running repository instance with a metamodel administrator

  @AC1 @REQ-038
  Scenario: Listed solutions
    Given the architect created the solution "Cucumber List B"
    And the architect created the solution "cucumber List A"
    When the viewer lists the solutions
    Then "cucumber List A" is listed before "Cucumber List B" with status "draft" and the owner's name

  @REQ-038
  Scenario: Unknown solution
    When the viewer reads a solution with an unknown ID
    Then the response status is 404

  @REQ-038
  Scenario: Without entity:read
    When a portal reader lists the solutions
    Then the response status is 403
