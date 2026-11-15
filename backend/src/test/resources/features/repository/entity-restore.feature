# Spec: openspec repository/entity-restore
# Source material: requirements/user-stories/US-101-entity-full-restore.md, US-102-restore-audit-snapshot.md, US-103-restore-transaction.md, REQ-101, REQ-102, REQ-103, REQ-162, UC-15

@US-101 @US-102 @US-103 @UC-15 @repository
Feature: Restore an entity to an earlier version

  Architects undo wrong changes by restoring an entity to an earlier version.
  The preview shows what the restore would change and what it keeps; the
  restore writes one new version that names its target version, and the
  version before it stays in the history as the state before the restore.

  Background:
    Given a running repository instance with a metamodel administrator
    And the history persons "Michael" and "Kurt"
    And the restore entity "CRM System" with 5 versions by "Kurt"

  # Requirement: Preview of a restore

  @US-101 @AC2 @REQ-101
  Scenario: Differing fields
    Given the restore property "owner" mapped as "optional"
    And the values of "description" of "CRM System" by version are "Old, Old, Old, Old, New"
    And the values of "owner" of "CRM System" by version are "Kurt, Kurt, Kurt, Michael, Michael"
    When the enterprise architect previews the restore of "CRM System" to version 3
    Then the preview names version 5 as current
    And the preview lists only "description, owner"
    And the preview lists "description" from "New" to "Old" as restored
    And the preview lists "owner" from "Michael" to "Kurt" as restored

  # Requirement: Fields not restored

  @REQ-101 @AC4 @REQ-162
  Scenario: Write-protected property in the preview
    Given the property "investmentCost" readable only by "enterprise-architect"
    And "investmentCost" is mapped to the metatype of "CRM System" from "2026-01-01"
    And the values of "investmentCost" of "CRM System" by version are "50, 50, 50, 100, 100"
    When the enterprise architect previews the restore of "CRM System" to version 3
    Then the preview lists "investmentCost" as not restored because "write-protected"

  @UC-15 @REQ-101
  Scenario: Property no longer mapped
    Given the public property "legacyCode"
    And the values of "legacyCode" of "CRM System" by version are "L-1, L-1, L-1, -, -"
    When the enterprise architect previews the restore of "CRM System" to version 3
    Then the preview lists "legacyCode" as not restored because "not-mapped"

  @UC-15 @REQ-101
  Scenario: Value no longer accepted
    Given the restore enumeration "lifecycleStatus" with the values "planned, active"
    And the enumeration values of "lifecycleStatus" of "CRM System" by version are "retired, retired, retired, active, active"
    When the enterprise architect previews the restore of "CRM System" to version 3
    Then the preview lists "lifecycleStatus" as not restored because "invalid-value"

  @US-101 @AC4 @REQ-101 @REQ-162
  Scenario: Write-protected property is kept
    Given the restore property "owner" mapped as "optional"
    And the property "investmentCost" readable only by "enterprise-architect"
    And "investmentCost" is mapped to the metatype of "CRM System" from "2026-01-01"
    And the values of "owner" of "CRM System" by version are "Kurt, Kurt, Kurt, Michael, Michael"
    And the values of "investmentCost" of "CRM System" by version are "50, 50, 50, 100, 100"
    When the enterprise architect restores "CRM System" to version 3
    Then the restore is answered with 200
    And "owner" of "CRM System" is "Kurt"
    And "investmentCost" of "CRM System" is "100"
    And the restore lists "investmentCost" as not restored because "write-protected"

  # Requirement: Warnings and redaction in the preview

  @UC-15 @REQ-101
  Scenario: Empty mandatory property
    Given the restore property "owner" mapped as "mandatory"
    And the values of "owner" of "CRM System" by version are "-, -, -, -, Kurt"
    When the enterprise architect previews the restore of "CRM System" to version 3
    Then the preview lists "owner" from "Kurt" to "-" as restored
    And the preview lists a warning for "owner"

  @REQ-101 @AC5 @REQ-160
  Scenario: Redacted value in the preview
    Given the property "investmentCost" readable only by "viewer"
    And "investmentCost" is mapped to the metatype of "CRM System" from "2026-01-01"
    And the values of "investmentCost" of "CRM System" by version are "50, 50, 50, 100, 100"
    When the enterprise architect previews the restore of "CRM System" to version 3
    Then the preview lists "investmentCost" as redacted

  # Requirement: Full restore of an entity

  @US-101 @AC1 @REQ-101
  Scenario: Restore to an earlier version
    Given the restore property "owner" mapped as "optional"
    And the values of "description" of "CRM System" by version are "Old, Old, Old, New, New"
    And the values of "owner" of "CRM System" by version are "Kurt, Kurt, Kurt, Michael, Michael"
    When the enterprise architect restores "CRM System" to version 3
    Then the restore is answered with 200
    And "description" of "CRM System" is "Old"
    And "owner" of "CRM System" is "Kurt"
    And "CRM System" keeps its ID and metatype

  @US-101 @AC1 @REQ-101
  Scenario: Value empty in the target version
    Given the restore property "website" mapped as "optional"
    And the values of "website" of "CRM System" by version are "-, -, -, -, https://crm.example.org"
    When the enterprise architect restores "CRM System" to version 3
    Then the restore is answered with 200
    And "website" of "CRM System" has no value

  @US-101 @REQ-101
  Scenario: Endpoints of a connection
    Given the restore connection "Flow" with 2 versions by "Kurt" whose name was "Old flow"
    When the enterprise architect restores "Flow" to version 1
    Then the restore is answered with 200
    And the name of "Flow" is "Old flow" and its endpoints are unchanged

  # Requirement: Version and result of a restore

  @US-102 @AC1 @AC3 @REQ-102
  Scenario: Restore version with reason
    Given the values of "description" of "CRM System" by version are "Old, Old, Old, New, New"
    And the enterprise architect has read the state of version 5 of "CRM System"
    When the enterprise architect restores "CRM System" to version 3 with the reason "Wrong owner"
    Then the restore is answered with 200
    And version 6 of "CRM System" records the restore from version 3 with the reason "Wrong owner"
    And version 5 of "CRM System" still has its state from before the restore

  @UC-15 @REQ-101
  Scenario: Missing mandatory value after the restore
    Given the restore property "owner" mapped as "mandatory"
    And the values of "owner" of "CRM System" by version are "-, -, -, -, Kurt"
    When the enterprise architect restores "CRM System" to version 3
    Then the restore is answered with 200
    And the restore lists a warning for "owner"
    And "CRM System" is marked as missing "owner"

  # Requirement: Protection against lost updates

  @UC-15 @REQ-101
  Scenario: Current version
    Given the values of "description" of "CRM System" by version are "Old, Old, Old, New, New"
    When the enterprise architect restores "CRM System" to version 3 with If-Match "\"5\""
    Then the restore is answered with 200

  @UC-15 @REQ-101
  Scenario: Changed meanwhile
    Given the values of "description" of "CRM System" by version are "Old, Old, Old, New, New"
    When the enterprise architect restores "CRM System" to version 3 with If-Match "\"4\""
    Then the restore is answered with 412 of type "stale-version" naming version 5
    And "CRM System" is still at version 5

  @UC-15 @REQ-101
  Scenario: Without If-Match
    When the enterprise architect restores "CRM System" to version 3 without If-Match
    Then the restore is answered with 428 of type "precondition-required"
    And "CRM System" is still at version 5

  # Requirement: Target version of a restore

  @US-101 @REQ-101
  Scenario Outline: Target version boundaries
    Given the values of "description" of "CRM System" by version are "Old, Old, Old, Old, New"
    When the enterprise architect previews the restore of "CRM System" to version <target>
    Then the restore is answered with <status>

    Examples:
      | target | status |
      | 1      | 200    |
      | 4      | 200    |
      | 0      | 400    |
      | 5      | 400    |
      | 6      | 404    |

  # Requirement: Reason, permission and empty restores

  @US-101 @REQ-101
  Scenario Outline: Reason length boundary
    Given the values of "description" of "CRM System" by version are "Old, Old, Old, New, New"
    When the enterprise architect restores "CRM System" to version 3 with a reason of <length> characters
    Then the restore is answered with <status>

    Examples:
      | length | status |
      | 1000   | 200    |
      | 1001   | 400    |

  @REQ-101 @REQ-162
  Scenario: Only write-protected differences
    Given the property "investmentCost" readable only by "enterprise-architect"
    And "investmentCost" is mapped to the metatype of "CRM System" from "2026-01-01"
    And the values of "investmentCost" of "CRM System" by version are "50, 50, 50, 100, 100"
    When the enterprise architect restores "CRM System" to version 3
    Then the restore is answered with 422 of type "nothing-to-restore"
    And "CRM System" is still at version 5

  @US-101 @REQ-101
  Scenario: Without restore permission
    Given the values of "description" of "CRM System" by version are "Old, Old, Old, New, New"
    When a person with "entity:write" but without "entity:restore" restores "CRM System" to version 3
    Then the restore is answered with 403
    And "CRM System" is still at version 5

  # Requirement: Atomic restore with audit

  @US-103 @AC2 @REQ-103
  Scenario: Successful restore
    Given the restore property "owner" mapped as "optional"
    And the values of "owner" of "CRM System" by version are "Kurt, Kurt, Kurt, Michael, Michael"
    When the enterprise architect restores "CRM System" to version 3 with the reason "Wrong owner"
    Then the restore is answered with 200
    And version 6 of "CRM System" records the restore from version 3 with the reason "Wrong owner"
    And an "entity.restored" event names version 6 of "CRM System", the target version 3 and the changed field "owner"
