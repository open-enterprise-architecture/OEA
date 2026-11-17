# Spec: openspec solution/solution-go-live, solution/solution-deltas, repository/entity-batch-write,
# repository/entity-restore, catalog/catalog-query, repository/entity-history (us-153-open-issues)
# Source material: requirements/user-stories/US-153-resolve-open-issues.md (owner decisions 2026-11-17)

@US-153 @UC-05 @solution
Feature: Open issues of a go-live block their entities until they are resolved

  The go-live took two metamodel violations live: "Salesforce" misses the
  mandatory "Contact", and "ERP-Core" has 250 "Users" where at most 100 are
  accepted. Each open issue blocks its entity until it is resolved with a
  valid value, by removing an optional value, or automatically once the
  violation no longer holds. The scenarios for a person with only
  entity:write run in OpenIssuesTests, since the development realm has no
  such user.

  Background:
    Given a running repository instance with a metamodel administrator
    And the architect created a solution for the deltas
    And the applications have the mandatory property "Owner" and the integer property "Users"
    And the landscape holds the application "ERP-Core"
    And the catalog value "Owner" of "ERP-Core" is "IT"
    And the catalog value "Users" of "ERP-Core" is "10"
    And the architect captured the new application "Salesforce" with "Owner" "Sales"
    And the architect modified "ERP-Core" with "Users" "250"
    And the applications get the mandatory property "Contact" after the capture
    And "Users" accepts at most 100 after the capture
    And the solution went live with its open issues

  # Requirement: Status of an open issue

  @AC4
  Scenario: Resolved automatically by a metamodel change
    When "Contact" of the applications is made optional
    Then the open issue "Contact" of "Salesforce" is resolved with "automatic"
    And the solution counts 1 open issue

  Scenario: Still open
    Then the open issue "Contact" of "Salesforce" is "open"
    And the solution counts 2 open issues

  # Requirement: Resolve open issues

  @AC2
  Scenario: Resolve with a valid value
    When the architect resolves the issue "Contact" of "Salesforce" with "sales@example.com"
    Then the answer is 200
    And the open issue "Contact" of "Salesforce" is resolved with "value"
    And the value of "Contact" of "Salesforce" is "sales@example.com"

  @AC3
  Scenario Outline: Boundary of the maximum
    When the architect resolves the issue "Users" of "ERP-Core" with "<value>"
    Then the answer is <status>
    And the value of "Users" of "ERP-Core" is "<stored>"

    Examples:
      | value | status | stored |
      | 100   | 200    | 100    |
      | 101   | 422    | 250    |

  @AC3
  Scenario: Remove an optional value
    When the architect removes the value of the issue "Users" of "ERP-Core"
    Then the answer is 200
    And the open issue "Users" of "ERP-Core" is resolved with "removed"
    And the catalog holds no value of "Users" for "ERP-Core"

  @AC3
  Scenario: Remove a mandatory value
    When the architect removes the value of the issue "Contact" of "Salesforce"
    Then the answer is 422
    And the answer names the issue "Contact" of "Salesforce"

  Scenario: Issue already resolved
    Given the architect removed the value of the issue "Users" of "ERP-Core"
    When the architect removes the value of the issue "Users" of "ERP-Core"
    Then the answer is 422
    And the answer names the issue "Users" of "ERP-Core"

  # Requirement: Permission to resolve open issues

  Scenario: Viewer
    When the viewer resolves the issue "Contact" of "Salesforce" with "sales@example.com"
    Then the answer is 403

  # Requirement: Entities blocked by open issues

  @AC1
  Scenario: Go-live of another solution
    Given the solution "ERP Extension" retired "ERP-Core" before the go-live
    When the architect takes the solution "ERP Extension" live
    Then the answer is 409 of type "entity-blocked"
    And the answer names the delta of "ERP-Core" in the solution "ERP Extension"
    And the answer names the issue "Users" of "ERP-Core"

  @AC1
  Scenario: Unblocked after the resolution
    Given the solution "ERP Extension" retired "ERP-Core" before the go-live
    And the architect removed the value of the issue "Users" of "ERP-Core"
    When the architect takes the solution "ERP Extension" live
    Then the answer is 200

  # Requirement: Deltas of a blocked entity

  @AC1
  Scenario: Modify a blocked entity
    When the architect modifies "ERP-Core" in the solution "ERP Extension"
    Then the answer is 409 of type "entity-blocked"
    And the solution "ERP Extension" has no deltas

  @AC1
  Scenario: Retire a blocked entity
    When the architect retires "ERP-Core" in the solution "ERP Extension"
    Then the answer is 409 of type "entity-blocked"

  Scenario: Entity without open issues
    Given the architect removed the value of the issue "Users" of "ERP-Core"
    When the architect retires "ERP-Core" in the solution "ERP Extension"
    Then the answer is 201

  # Requirement: Updates of a blocked entity in a batch

  @AC1
  Scenario Outline: Update of a blocked entity
    Given "Contact" of the applications becomes optional
    And the landscape holds the application "Other"
    And "ERP-Core" has an external ID
    And "Other" has an external ID
    When the architect writes "ERP-Core" and "Other" in a batch with atomic <atomic>
    Then the batch item of "ERP-Core" fails with "OPEN_ISSUES" and the item of "Other" is <outcome>

    Examples:
      | atomic | outcome     |
      | false  | written     |
      | true   | unchanged   |

  # Requirement: Restore of a blocked entity

  @AC1
  Scenario: Restore a blocked entity
    When the architect restores "ERP-Core" to version 1
    Then the answer is 409 of type "entity-blocked"

  Scenario: Restore after the resolution
    Given the architect removed the value of the issue "Users" of "ERP-Core"
    When the architect restores "ERP-Core" to version 1
    Then the answer is not 409

  # Requirement: Entities with open issues in the catalog

  @AC5
  Scenario: Marked row
    When the architect queries a catalog of the applications
    Then the row of "ERP-Core" is marked true

  @AC5
  Scenario: Row without issues
    Given the architect removed the value of the issue "Users" of "ERP-Core"
    When the architect queries a catalog of the applications
    Then the row of "ERP-Core" is marked false

  # Requirement: Open issues on the history of an entity

  @AC5
  Scenario: Notice on the history
    When the architect reads the history of "Salesforce"
    Then the history names the open issue "Contact" of the solution

  @AC5
  Scenario: No notice
    Given the architect removed the value of the issue "Users" of "ERP-Core"
    When the architect reads the history of "ERP-Core"
    Then the history names no open issue
