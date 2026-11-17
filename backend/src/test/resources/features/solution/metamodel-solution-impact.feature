# Spec: openspec metamodel/property-mappings, metamodel/property-definitions, metamodel/metatype-editing,
# metamodel/solution-extensions, solution/solution-deltas, solution/solution-go-live (us-153-open-issues)
# Source material: requirements/user-stories/US-154-metamodel-change-in-solutions.md (owner decisions 2026-11-17)

@US-154 @UC-04 @UC-05 @solution
Feature: A metamodel change asks whether it applies to active solutions

  Removing a mapping, deleting an enumeration value or changing a base type
  removes values from the catalog. For every active solution the change
  affects, OEA asks whether the change applies there too ("apply") or whether
  the solution keeps the property as a snapshot until its go-live ("keep").
  At the go-live, a snapshot value that is not discarded becomes an open issue.

  Background:
    Given a running repository instance with a metamodel administrator
    And the architect created a solution for the deltas
    And the applications have the mandatory property "Owner" and the integer property "Users"
    And the applications have the optional property "Cost Center"
    And the applications have the enumeration property "Tier" with the values "high" and "low"
    And the landscape holds the application "ERP-Core"
    And the catalog value "Owner" of "ERP-Core" is "IT"
    And the architect captured the new application "Salesforce" with the cost center "CC-100" and the tier "high"
    And the architect modified "ERP-Core" with "Cost Center" "CC-2"

  # Requirement: Metamodel changes in active solutions

  @AC1
  Scenario: Asked for an affected solution
    When the metamodel administrator removes "Cost Center" from the applications
    Then the answer is 409 of type "solution-decision-required"
    And the answer lists the solutions "main"
    And the mapping of "Cost Center" to the applications is kept

  @AC5
  Scenario: No affected solution
    Given the applications have the optional property "Budget"
    When the metamodel administrator removes "Budget" from the applications
    Then the answer is 204

  @AC5
  Scenario: Implemented solution not asked
    Given the solution is in status "implemented"
    When the metamodel administrator removes "Cost Center" from the applications
    Then the answer is 204

  @AC1
  Scenario: Decision missing for one solution
    Given the solution "ERP Extension" captured the new application "Workday" with "Cost Center" "CC-7"
    When the metamodel administrator removes "Cost Center" from the applications deciding only for the solution
    Then the answer is 409 of type "solution-decision-required"
    And the answer lists the solutions "main, ERP Extension"
    And the mapping of "Cost Center" to the applications is kept

  # Requirement: Apply a metamodel change to a solution

  @AC2
  Scenario: Values removed from the deltas
    When the metamodel administrator removes "Cost Center" from the applications applying it to the solution
    Then the answer is 204
    And the delta of "Salesforce" has no cost center
    And the audit names the decision "apply"

  @AC2
  Scenario: Modification left empty
    When the metamodel administrator removes "Cost Center" from the applications applying it to the solution
    Then the modification of "ERP-Core" is removed

  # Requirement: Keep a snapshot in a solution

  @AC3
  Scenario: Snapshot kept
    When the metamodel administrator removes "Cost Center" from the applications keeping it in the solution
    Then the answer is 204
    And the catalog holds no value of "Cost Center" for "ERP-Core"
    And the delta of "Salesforce" has the cost center "CC-100"
    And the solution keeps a snapshot of "Cost Center"

  @AC3
  Scenario: New delta with a snapshot property
    Given the metamodel administrator removed "Cost Center" from the applications keeping it in the solution
    When the architect captures the new application "Workday" with the cost center "CC-7" in the solution "main"
    Then the answer is 201
    And the delta of "Workday" has the cost center "CC-7"

  # Requirement: Snapshots in the checks of deltas

  @AC3
  Scenario: Snapshot property in another solution
    Given the metamodel administrator removed "Cost Center" from the applications keeping it in the solution
    When the architect captures the new application "Workday" with the cost center "CC-7" in the solution "ERP Extension"
    Then the answer is 201

  # Requirement: Snapshots in the effective metamodel of an extension

  @AC3
  Scenario: Snapshot in the extension
    Given the solution has a metamodel extension
    And the metamodel administrator removed "Cost Center" from the applications keeping it in the solution
    When the architect reads the metatypes of the extension
    Then the applications list "Cost Center" as a snapshot

  # Requirement: Deleted enumeration values in active solutions

  @AC1
  Scenario: Enumeration value used in a delta
    When the metamodel administrator deletes the enumeration value "high" of "Tier"
    Then the answer is 409 of type "solution-decision-required"
    When the metamodel administrator deletes the enumeration value "high" of "Tier" keeping it in the solution
    Then the answer is 204
    And the delta of "Salesforce" has the tier "high"

  @AC3
  Scenario: Definition with a snapshot
    Given the metamodel administrator removed "Cost Center" from the applications keeping it in the solution
    When the metamodel administrator deletes the definition "Cost Center"
    Then the answer is 409 of type "in-use"

  # Requirement: Base type changes in active solutions

  @AC1
  Scenario: Inherited property lost
    Given the metatype "Cloud Service" is based on the applications
    And the architect captured the new "Cloud Service" "AWS" with "Cost Center" "CC-9"
    When the metamodel administrator removes the base type of "Cloud Service"
    Then the answer is 409 of type "solution-decision-required"
    When the metamodel administrator removes the base type of "Cloud Service" applying it to the solution
    Then the answer is 200

  Scenario: Base type change without lost properties
    Given the metatype "Cloud Service" is based on the applications
    And the metatype "Platform" is based on the applications
    When the metamodel administrator bases "Cloud Service" on "Platform"
    Then the answer is 200

  # Requirement: Snapshot values at the go-live

  @AC4
  Scenario: Value resolved later
    Given the metamodel administrator removed "Cost Center" from the applications keeping it in the solution
    Then the preview lists the cost center of "Salesforce" as not in the metamodel
    When the architect takes the solution live
    Then the go-live answers 200
    And the solution lists the open issue not in the metamodel keeping "CC-100"
    And the solution keeps no snapshot

  @AC4
  Scenario: Value discarded
    Given the metamodel administrator removed "Cost Center" from the applications keeping it in the solution
    When the architect discards the cost center of "Salesforce" in the merge
    Then the answer is 200
    When the architect takes the solution live
    Then the go-live answers 200
    And the solution lists no open issue keeping "CC-100"

  # Requirement: Resolve a value not in the metamodel

  @AC4
  Scenario: Discard the kept value
    Given the metamodel administrator removed "Cost Center" from the applications keeping it in the solution
    And the architect took the solution live
    When the architect discards the issue keeping "CC-100"
    Then the answer is 200
    And the issue keeping "CC-100" is resolved with "removed"

  @AC4
  Scenario: Write after the property is mapped again
    Given the metamodel administrator removed "Cost Center" from the applications keeping it in the solution
    And the architect took the solution live
    And the metamodel administrator mapped "Cost Center" to the applications again
    When the architect writes "CC-100" for the issue keeping "CC-100"
    Then the answer is 200
    And the issue keeping "CC-100" is resolved with "value"

  @AC4
  Scenario: Write while still not mapped
    Given the metamodel administrator removed "Cost Center" from the applications keeping it in the solution
    And the architect took the solution live
    When the architect writes "CC-100" for the issue keeping "CC-100"
    Then the answer is 422

  # us-084-plateaus – Requirements: Metamodel question per plateau, Decisions by plateau only

  @US-156 @AC1
  Scenario: Asked once per plateau
    Given the solution "ERP Extension" captured the new application "Workday" with "Cost Center" "CC-7"
    And the solution "Analytics" captured the new application "SAP" with "Cost Center" "CC-8"
    And the solutions "ERP Extension" and "Analytics" target the plateau "Target 2027"
    When the metamodel administrator removes "Cost Center" from the applications
    Then the answer is 409 of type "solution-decision-required"
    And the answer names the plateau for "ERP Extension" and "Analytics"

  @US-156 @AC1 @AC2
  Scenario: Decision by plateau
    Given the solution "ERP Extension" captured the new application "Workday" with "Cost Center" "CC-7"
    And the solution "Analytics" captured the new application "SAP" with "Cost Center" "CC-8"
    And the solutions "ERP Extension" and "Analytics" target the plateau "Target 2027"
    When the metamodel administrator removes "Cost Center" from the applications keeping it for the plateau and applying it to the solution
    Then the answer is 204
    And the solution "ERP Extension" keeps a snapshot of "Cost Center"
    And the solution "Analytics" keeps a snapshot of "Cost Center"
    And the delta of "Salesforce" has no cost center

  @US-156 @AC3
  Scenario: Decision for a solution of a plateau
    Given the solution "ERP Extension" captured the new application "Workday" with "Cost Center" "CC-7"
    And the solution "Analytics" captured the new application "SAP" with "Cost Center" "CC-8"
    And the solutions "ERP Extension" and "Analytics" target the plateau "Target 2027"
    When the metamodel administrator removes "Cost Center" from the applications deciding for the solution "ERP Extension" of the plateau
    Then the answer is 422
    And the mapping of "Cost Center" to the applications is kept
