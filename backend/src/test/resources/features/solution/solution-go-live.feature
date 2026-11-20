# Spec: openspec solution/solution-go-live
# Source material: requirements/user-stories/US-152-solution-go-live.md (owner decisions 2026-11-16)

@US-152 @UC-05 @solution
Feature: Go-live of a solution in project mode

  A solution goes live in one transaction: new entities are created, modified
  entities get the values of their deltas, retired entities are deleted with
  their connections. A field changed in the catalog after the capture must be
  merged first; metamodel violations become open issues.

  Background:
    Given a running repository instance with a metamodel administrator
    And the architect created a solution for the deltas
    And the applications have the mandatory property "Owner" and the integer property "Users"
    And the landscape holds the application "ERP-Core"
    And the landscape holds the application "CRM-Legacy"
    And the catalog value "Owner" of "ERP-Core" is "IT"
    And the catalog value "Users" of "ERP-Core" is "10"
    And the connection "CRM to ERP" leads from "CRM-Legacy" to "ERP-Core"

  # Requirement: Preview of a go-live

  @AC5
  Scenario: Counts of a solution
    Given the architect captured the new application "Salesforce" with "Owner" "Sales"
    And the architect modified "ERP-Core" with "Users" "20"
    And the architect retired "CRM-Legacy"
    When the architect previews the go-live
    Then the preview counts 1 new, 1 modified, 1 retired entities and 1 retired connection

  # Requirement: Field conflicts

  @AC2
  Scenario: Field changed after the capture
    Given the architect modified "ERP-Core" with "Users" "20"
    And the catalog value "Users" of "ERP-Core" is changed to "15"
    When the architect previews the go-live
    Then the preview lists a conflict for "Users" with before "10", current "15" and delta "20"

  @AC3
  Scenario: Other field changed after the capture
    Given the architect modified "ERP-Core" with "Users" "20"
    And the catalog value "Owner" of "ERP-Core" is changed to "Finance"
    When the architect previews the go-live
    Then the preview lists no conflict

  # Requirement: Resolution of field conflicts

  @AC2
  Scenario Outline: Resolution of a conflict
    Given the architect modified "ERP-Core" with "Users" "20"
    And the catalog value "Users" of "ERP-Core" is changed to "15"
    When the architect resolves "Users" of "ERP-Core" with the choice "<choice>" and the value "<value>"
    Then the resolution answers 200
    And the preview lists no conflict
    When the architect takes the solution live
    Then the go-live answers 200
    And the catalog value "Users" of "ERP-Core" reads "<result>"

    Examples:
      | choice  | value | result |
      | delta   |       | 20     |
      | current |       | 15     |
      | value   | 18    | 18     |

  @AC2
  Scenario: Field without a conflict
    Given the architect modified "ERP-Core" with "Users" "20"
    And the catalog value "Users" of "ERP-Core" is changed to "15"
    When the architect resolves "Owner" of "ERP-Core" with the choice "delta" and the value ""
    Then the resolution answers 422 naming "Owner"

  # Requirement: Go-live of a solution

  @AC2
  Scenario: Unresolved conflict
    Given the architect modified "ERP-Core" with "Users" "20"
    And the catalog value "Users" of "ERP-Core" is changed to "15"
    When the architect takes the solution live
    Then the go-live answers 409 of type "merge-required"
    And the catalog value "Users" of "ERP-Core" reads "15"
    And the solution is in status "draft" after the go-live

  Scenario: Implemented solution
    Given the architect modified "ERP-Core" with "Users" "20"
    And the architect took the solution live
    When the architect takes the solution live
    Then the go-live answers 409 of type "solution-completed"

  Scenario: Without permission
    Given the architect modified "ERP-Core" with "Users" "20"
    When the viewer takes the solution live
    Then the go-live answers 403
    And the solution is in status "draft" after the go-live

  # Requirement: Effects of a go-live

  @AC1
  Scenario: New, modified and retired
    Given the architect captured the new application "Salesforce" with "Owner" "Sales"
    And the architect modified "ERP-Core" with "Users" "20"
    And the architect retired "CRM-Legacy"
    When the architect takes the solution live
    Then the go-live answers 200
    And the new application "Salesforce" exists with "Owner" "Sales"
    And the catalog value "Users" of "ERP-Core" reads "20"
    And "CRM-Legacy" and the connection "CRM to ERP" are deleted
    And the solution is in status "implemented" after the go-live

  # Requirement: Deltas on deleted entities (s17-entity-delete)

  Scenario Outline: Conflict after a deletion
    Given the architect <capture>
    And "<entity>" is deleted from the catalog
    When the architect previews the go-live
    Then the preview lists a deletion conflict of type "<type>" for "<entity>"
    When the architect takes the solution live
    Then the go-live answers 409 of type "entity-deleted"
    And the solution is in status "draft" after the go-live

    Examples:
      | capture                                     | entity     | type     |
      | modified "ERP-Core" with "Users" "20"       | ERP-Core   | modified |
      | retired "CRM-Legacy"                        | CRM-Legacy | retiring |

  Scenario: Resolved by removing the delta
    Given the architect modified "ERP-Core" with "Users" "20"
    And "ERP-Core" is deleted from the catalog
    When the architect removes the delta of "ERP-Core"
    And the architect takes the solution live
    Then the go-live answers 200

  Scenario: Resolved by restoring the entity
    Given the architect modified "ERP-Core" with "Users" "20"
    And "ERP-Core" is deleted from the catalog
    And the architect undeletes "ERP-Core"
    When the architect takes the solution live
    Then the go-live answers 200
    And the catalog value "Users" of "ERP-Core" reads "20"

  Scenario: Entities that are not deleted
    Given the architect modified "ERP-Core" with "Users" "20"
    And "CRM-Legacy" is deleted from the catalog
    Then the preview lists no deletion conflict

  # Requirement: Open issues of a go-live

  @AC4
  Scenario: Mandatory property missing
    Given the architect captured the new application "Salesforce" with "Owner" "Sales"
    And the applications get the mandatory property "Contact" after the capture
    When the architect takes the solution live
    Then the go-live answers 200
    And the solution lists the open issue "mandatory-missing" for "Contact" of "Salesforce"

  @AC4
  Scenario: Value no longer accepted
    Given the architect modified "ERP-Core" with "Users" "250"
    And "Users" accepts at most 100 after the capture
    When the architect takes the solution live
    Then the go-live answers 200
    And the catalog value "Users" of "ERP-Core" reads "250"
    And the solution lists the open issue "invalid-value" for "Users" of "ERP-Core"

  # Requirement: Audit of a go-live

  Scenario: Events of a go-live
    Given the architect captured the new application "Salesforce" with "Owner" "Sales"
    And the architect modified "ERP-Core" with "Users" "20"
    And the architect retired "CRM-Legacy"
    When the architect takes the solution live
    Then the audit log holds "solution.went-live" with 1 new, 1 modified and 1 retired
