# Spec: openspec solution/solution-creation
# Source material: requirements/user-stories/US-038-create-solution.md; REQ-038; ADR-032 amendment 2026-11-12

@US-038 @UC-05 @solution
Feature: Create a solution in project mode

  A solution architect creates a solution as the container of a change
  initiative, with a unique name and an architecture vision. Plateau mode
  follows with the plateaus.

  Background:
    Given a running repository instance with a metamodel administrator

  @AC1 @REQ-038
  Scenario: Solution created
    When the architect creates the solution "Cucumber ERP Extension Logistics" with the vision "Logistics in the ERP"
    Then the solution "Cucumber ERP Extension Logistics" is a draft owned by the architect without plateaus
    And its creation is audited as "solution.created"

  @AC1 @REQ-038
  Scenario: Name stored trimmed
    When the architect creates the solution "  Cucumber CRM Replacement  "
    Then the solution "Cucumber CRM Replacement" is a draft owned by the architect without plateaus

  @AC2 @REQ-038
  Scenario: Name taken
    Given the architect created the solution "Cucumber Taken"
    When the architect creates the solution "cucumber taken "
    Then the creation is rejected with 422 naming "name" and the detail "Name 'cucumber taken' is already taken"
    And exactly one solution is named "Cucumber Taken"

  @REQ-038
  Scenario Outline: Limits of name and vision
    When the architect creates a solution with a name of <name> characters and a vision of <vision> characters
    Then the response status is <status>

    Examples:
      | name | vision | status |
      | 255  | 0      | 201    |
      | 256  | 0      | 400    |
      | 40   | 2000   | 201    |
      | 40   | 2001   | 400    |

  @REQ-038
  Scenario: Blank name
    When the architect creates the solution "   "
    Then the creation is rejected with 422 naming "name"

  @AC5 @REQ-038
  Scenario: Without permission
    When the viewer creates the solution "Cucumber Forbidden"
    Then the response status is 403
    And no solution is named "Cucumber Forbidden"

  @AC5 @REQ-157
  Scenario: Built-in roles
    When the authorization roles are listed
    Then "solution-architect" holds exactly "portal:read, entity:read, solution:write, solution-metamodel:write" and is not privileged
    And "enterprise-architect" holds "solution:write"
