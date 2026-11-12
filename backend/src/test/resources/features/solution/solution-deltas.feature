# Spec: openspec solution/solution-deltas
# Source material: requirements/user-stories/US-040-delta-new-entity.md, US-042-delta-retiring.md; REQ-040 (realigned 2026-11-12)

@US-040 @US-041 @US-042 @UC-05 @solution
Feature: Entity deltas of a solution

  A solution captures the entities an initiative introduces (new) and the
  entities of the baseline it retires (retiring). A new entity is checked
  like the write of an entity, and no entity is written before the go-live.
  Each scenario works on its own metatypes, properties and solutions.

  Background:
    Given a running repository instance with a metamodel administrator
    And the architect created a solution for the deltas
    And the applications have the mandatory property "Owner" and the integer property "Users"

  # Requirement: Capture a new entity

  @AC1 @REQ-040
  Scenario: New entity
    When the architect captures the new application "Salesforce" with "Owner" "Sales"
    Then the capture answers 201
    And the deltas list exactly "new Salesforce"
    And the baseline does not list "Salesforce"

  @AC1 @REQ-040
  Scenario: New entity with property values
    When the architect captures the new application "Salesforce" with "Owner" "Sales" and "Users" "250"
    Then the capture answers 201
    And the delta "Salesforce" holds "Owner" "Sales" and "Users" "250"

  # Requirement: Checks of a new entity

  @AC4 @REQ-040
  Scenario: Unknown metatype
    When the architect captures a new entity "Salesforce" of an unknown metatype
    Then the capture answers 422 naming "metatypeId"
    And the solution holds no delta

  @AC4 @REQ-040
  Scenario: Connection metatype
    When the architect captures a new entity "Orders" of the data flow metatype
    Then the capture answers 422 naming "metatypeId"

  @AC4 @REQ-040
  Scenario: Invalid property value
    When the architect captures the new application "Salesforce" with "Owner" "Sales" and "Users" "many"
    Then the capture answers 422 naming "properties[1].value"
    And the solution holds no delta

  @AC4 @REQ-040
  Scenario: Mandatory property missing
    When the architect captures the new application "Salesforce" without properties
    Then the capture answers 422 naming "properties"

  @REQ-040
  Scenario Outline: Length of the name
    When the architect captures a new application with a name of <length> characters
    Then the capture answers <status>

    Examples:
      | length | status |
      | 255    | 201    |
      | 256    | 422    |
      | 0      | 422    |

  @REQ-040
  Scenario Outline: Length of the description
    When the architect captures a new application with a description of <length> characters
    Then the capture answers <status>

    Examples:
      | length | status |
      | 2000   | 201    |
      | 2001   | 422    |

  # Requirement: Visibility of the values of a new entity

  @REQ-124 @REQ-040
  Scenario: Restricted property with a write role
    Given the property "Cost" of the applications is role-restricted to the role "enterprise-architect"
    When the architect captures the new application "Salesforce" with "Owner" "Sales" and "Cost" "100"
    Then the capture answers 201

  @REQ-124 @REQ-040
  Scenario: Restricted property without a write role
    Given the property "Cost" of the applications is role-restricted to the role "auditor"
    When the architect captures the new application "Salesforce" with "Owner" "Sales" and "Cost" "100"
    Then the capture answers 403
    And the solution holds no delta

  @REQ-127 @REQ-040
  Scenario: Connection-scoped property
    Given the property "Salary Band" of the applications is connection-scoped
    When the architect captures the new application "Salesforce" with "Owner" "Sales" and "Salary Band" "B4"
    Then the capture answers 403

  @REQ-160 @REQ-040
  Scenario: Restricted value redacted on read
    Given the property "Cost" of the applications is role-restricted to the role "enterprise-architect"
    And the architect captured the new application "Salesforce" with "Owner" "Sales" and "Cost" "100"
    Then the viewer reads "Cost" of "Salesforce" as redacted
    And the architect reads "Cost" of "Salesforce" as "100"

  # Requirement: Retire an entity of the baseline

  @AC1 @US-042 @REQ-040
  Scenario: Retire an entity
    Given the landscape holds the application "CRM-Legacy"
    When the architect retires "CRM-Legacy"
    Then the capture answers 201
    And the deltas list exactly "retiring CRM-Legacy"

  @AC2 @US-042 @REQ-040
  Scenario: Entity not in the baseline
    When the architect retires an entity ID that does not exist
    Then the capture answers 422 naming "entityId" with the detail "Entity not found in the current landscape"
    And the solution holds no delta

  # Requirement: One delta per entity

  @AC3 @REQ-040
  Scenario: Second new entity with the same name
    Given the architect captured the new application "Salesforce" with "Owner" "Sales"
    When the architect captures the new application "salesforce" with "Owner" "Sales"
    Then the capture answers 422 naming "name" with the detail "A delta already exists for this entity in this solution"

  @AC3 @REQ-040
  Scenario: Same name, other metatype
    Given the architect captured the new application "Salesforce" with "Owner" "Sales"
    When the architect captures the new component "Salesforce"
    Then the capture answers 201

  @AC3 @US-042 @REQ-040
  Scenario: Entity retired twice
    Given the landscape holds the application "CRM-Legacy"
    And the architect retired "CRM-Legacy"
    When the architect retires "CRM-Legacy"
    Then the capture answers 422 naming "entityId" with the detail "A delta already exists for this entity in this solution"

  @US-042 @REQ-040
  Scenario: Other solution
    Given the landscape holds the application "CRM-Legacy"
    And the architect retired "CRM-Legacy"
    When the architect retires "CRM-Legacy" in another solution
    Then the capture answers 201

  # Requirement: Limit of deltas per solution

  @REQ-040
  Scenario: Limit of deltas
    Given the solution holds 999 deltas
    When the architect captures the new component "Last"
    Then the capture answers 201
    When the architect captures the new component "Beyond"
    Then the capture answers 422 naming "deltas"

  # Requirement: Completed solution

  @AC2 @US-042 @REQ-040
  Scenario: Implemented solution
    Given the landscape holds the application "CRM-Legacy"
    And the architect retired "CRM-Legacy"
    And the solution is in status "implemented"
    When the architect captures the new application "Salesforce" with "Owner" "Sales"
    Then the capture answers 409 with the detail "This solution is completed and can no longer be changed"
    When the architect removes the delta of "CRM-Legacy"
    Then the removal answers 409
    And the deltas list exactly "retiring CRM-Legacy"

  @AC2 @REQ-040
  Scenario: Archived solution
    Given the solution is in status "archived"
    When the architect captures the new application "Salesforce" with "Owner" "Sales"
    Then the capture answers 409

  @REQ-040
  Scenario: Solution in progress
    Given the solution is in status "in-progress"
    When the architect captures the new application "Salesforce" with "Owner" "Sales"
    Then the capture answers 201

  # Requirement: Read the deltas

  @REQ-040
  Scenario: Delta list
    Given the landscape holds the application "CRM-Legacy"
    And the architect retired "CRM-Legacy"
    And the architect captured the new application "Salesforce" with "Owner" "Sales"
    Then the deltas list exactly "new Salesforce, retiring CRM-Legacy"
    And the delta "CRM-Legacy" carries its entity ID

  @REQ-040
  Scenario: No deltas
    Then the deltas list exactly ""

  @REQ-040
  Scenario: Unknown solution
    When the architect reads the deltas of an unknown solution
    Then the read answers 404
    When the architect retires an entity ID that does not exist in an unknown solution
    Then the capture answers 404

  # Requirement: Remove a delta

  @REQ-040
  Scenario: Remove a delta
    Given the landscape holds the application "CRM-Legacy"
    And the architect retired "CRM-Legacy"
    When the architect removes the delta of "CRM-Legacy"
    Then the removal answers 204
    And the deltas list exactly ""
    When the architect retires "CRM-Legacy"
    Then the capture answers 201

  @REQ-040
  Scenario: Delta of another solution
    Given the landscape holds the application "CRM-Legacy"
    And the architect retired "CRM-Legacy" in another solution
    When the architect removes that delta through the first solution
    Then the removal answers 404
    And the other solution still holds 1 delta

  # Requirement: Permissions for deltas

  @REQ-040
  Scenario: Other solution architect
    Given the landscape holds the application "CRM-Legacy"
    When another holder of solution:write retires "CRM-Legacy"
    Then the capture answers 201

  @REQ-040
  Scenario: Without solution:write
    Given the landscape holds the application "CRM-Legacy"
    And the architect retired "CRM-Legacy"
    When the viewer retires "CRM-Legacy"
    Then the capture answers 403
    When the viewer removes the delta of "CRM-Legacy"
    Then the removal answers 403

  @REQ-040
  Scenario: Without entity:read
    When a portal reader reads the deltas
    Then the read answers 403

  # Requirement: Audit of deltas

  @REQ-040
  Scenario: Delta captured
    When the architect captures the new application "Salesforce" with "Owner" "Sales" and "Users" "250"
    Then the audit log holds "solution.delta-added" of type "new" naming "Salesforce" without the values

  @REQ-040
  Scenario: Delta removed
    Given the landscape holds the application "CRM-Legacy"
    And the architect retired "CRM-Legacy"
    When the architect removes the delta of "CRM-Legacy"
    Then the audit log holds "solution.delta-removed" of type "retiring" naming the entity of "CRM-Legacy"

  @REQ-040
  Scenario: Rejected capture
    When the architect captures the new application "Salesforce" without properties
    Then the capture answers 422 naming "properties"
    And the audit log holds no "solution.delta-added"

  # us-041-modified-delta – Requirement: Capture a modification

  @AC1 @US-041 @REQ-040
  Scenario: Modify a property
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    When the architect modifies "ERP-Core" with "Release" "v5"
    Then the capture answers 201
    And the delta of "ERP-Core" changes "Release" from "v4" to "v5"
    And the deltas list exactly "modified ERP-Core"
    And the catalog still holds "Release" "v4" for "ERP-Core"

  @US-041 @REQ-040
  Scenario: Rename and describe
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    When the architect modifies "ERP-Core" with the name "ERP-Core 2" and the description "Core ERP"
    Then the capture answers 201
    And the delta of "ERP-Core" changes the name to "ERP-Core 2" and the description

  @US-041 @REQ-040
  Scenario: Remove a value
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    When the architect modifies "ERP-Core" with an empty "Users"
    Then the capture answers 201
    And the delta of "ERP-Core" changes "Users" from "250" to "no value"

  @AC3 @US-041 @REQ-040
  Scenario: Modified entity not in the baseline
    When the architect modifies an entity ID that does not exist
    Then the capture answers 422 naming "entityId" with the detail "Entity not found in the current landscape"
    And the solution holds no delta

  # Requirement: Before values recorded by the server

  @AC2 @US-041 @REQ-040
  Scenario: Before value stays when the catalog changes
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the architect modified "ERP-Core" with "Release" "v5"
    And the catalog value of "Release" of "ERP-Core" changes to "v4.1"
    Then the delta of "ERP-Core" changes "Release" from "v4" to "v5"

  @AC2 @US-041 @REQ-040
  Scenario: Nothing changes
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    When the architect modifies "ERP-Core" with "Release" "v4"
    Then the capture answers 422 naming "properties"

  # Requirement: Checks of a modification

  @US-041 @REQ-040
  Scenario: Invalid value of a modification
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    When the architect modifies "ERP-Core" with "Users" "many"
    Then the capture answers 422 naming "properties[0].value"

  @US-041 @REQ-040
  Scenario: Mandatory value removed
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    When the architect modifies "ERP-Core" with an empty "Owner"
    Then the capture answers 422 naming "properties"

  @US-041 @REQ-040
  Scenario Outline: Length of the new name
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    When the architect modifies "ERP-Core" with a name of <length> characters
    Then the capture answers <status>

    Examples:
      | length | status |
      | 255    | 201    |
      | 256    | 422    |
      | 0      | 422    |

  @US-041 @REQ-040
  Scenario Outline: Length of the new description
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    When the architect modifies "ERP-Core" with a description of <length> characters
    Then the capture answers <status>

    Examples:
      | length | status |
      | 2000   | 201    |
      | 2001   | 422    |

  # Requirement: Visibility of a modification

  @US-041 @REQ-124 @REQ-040
  Scenario: Modification of a restricted property without a write role
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the property "Cost" of the applications is role-restricted to the role "auditor"
    When the architect modifies "ERP-Core" with "Cost" "100"
    Then the capture answers 403

  @US-041 @REQ-127 @REQ-040
  Scenario: Connection-scoped property outside the scope
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the property "Salary Band" of the applications is connection-scoped
    When the architect modifies "ERP-Core" with "Salary Band" "B4"
    Then the capture answers 403

  @US-041 @REQ-160 @REQ-040
  Scenario: Restricted values redacted on read
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the property "Cost" of the applications is role-restricted to the role "enterprise-architect"
    And the architect modified "ERP-Core" with "Cost" "100"
    Then the viewer reads the change of "Cost" of "ERP-Core" as redacted

  # Requirement: Edit a modification

  @US-041 @REQ-040
  Scenario: Edit the after value
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the architect modified "ERP-Core" with "Release" "v5"
    And the catalog value of "Release" of "ERP-Core" changes to "v4.1"
    When the architect edits the delta of "ERP-Core" to "Release" "v6" and "Users" "300"
    Then the edit answers 200
    And the delta of "ERP-Core" changes "Release" from "v4" to "v6"
    And the delta of "ERP-Core" changes "Users" from "250" to "300"

  @US-041 @REQ-160 @REQ-040
  Scenario: Change the editor may not read
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the property "Cost" of the applications is writable by the role "enterprise-architect" but readable only by the role "auditor"
    And the architect modified "ERP-Core" with "Release" "v5" and "Cost" "120"
    When the architect edits the delta of "ERP-Core" to "Release" "v6"
    Then the edit answers 200
    And the delta of "ERP-Core" changes "Release" from "v4" to "v6"
    And the delta of "ERP-Core" still records "Cost" with the after value "120"

  @US-041 @REQ-040
  Scenario: Edit a retiring delta
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the architect retired "ERP-Core"
    When the architect edits the delta of "ERP-Core" to "Release" "v5"
    Then the edit answers 422 naming "deltaType"

  @US-041 @REQ-040
  Scenario: Edit without solution:write
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the architect modified "ERP-Core" with "Release" "v5"
    When the viewer edits the delta of "ERP-Core" to "Release" "v6"
    Then the edit answers 403

  # Requirement: Audit of modifications

  @US-041 @REQ-040
  Scenario: Modification audited
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    When the architect modifies "ERP-Core" with "Release" "v5"
    Then the audit log holds "solution.delta-added" naming the field "Release" without "v5"
    When the architect edits the delta of "ERP-Core" to "Release" "v6"
    Then the audit log holds "solution.delta-updated" naming the field "Release" without "v6"

  # Requirement: One delta per entity (modified), Completed solution (edit)

  @AC5 @US-041 @REQ-040
  Scenario: Retired entity modified
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the architect retired "ERP-Core"
    When the architect modifies "ERP-Core" with "Release" "v5"
    Then the capture answers 422 naming "entityId" with the detail "A delta already exists for this entity in this solution"

  @AC4 @US-041 @REQ-040
  Scenario: Edit in an implemented solution
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the architect modified "ERP-Core" with "Release" "v5"
    And the solution is in status "implemented"
    When the architect modifies "ERP-Core" with "Users" "300"
    Then the capture answers 409
    When the architect edits the delta of "ERP-Core" to "Release" "v6"
    Then the edit answers 409
    And the delta of "ERP-Core" changes "Release" from "v4" to "v5"
