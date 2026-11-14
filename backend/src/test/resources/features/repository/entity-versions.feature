# Spec: openspec repository/entity-versions
# Source material: requirements/user-stories/US-099-version-history-diff.md, US-100-version-history-snapshot.md, REQ-099, REQ-100, REQ-160, REQ-128

@US-099 @US-100 @UC-14 @repository
Feature: Values of an entity's versions

  Architects see what a change did, and auditors prove how an entity looked at
  any version. The state of a version is reconstructed from the history; the
  diff lists only the fields whose value differs, and never reveals a value the
  reader may not read.

  Background:
    Given a running repository instance with a metamodel administrator
    And the history persons "Michael" and "Kurt"

  # Requirement: Complete state of a version; Properties of a state

  @US-100 @AC1 @REQ-100
  Scenario: Complete state with empty properties
    Given the history entity "CRM System" of the subtype "Versioned Component" of "Application Component" with 5 versions by "Kurt"
    And the public property "lifecycleStatus"
    And the public property "owner"
    And "lifecycleStatus" and "owner" are mapped to the metatype of "CRM System" from "2026-01-01"
    And the values of "lifecycleStatus" of "CRM System" by version are "planned, planned, planned, active, active"
    When the enterprise architect reads the state of version 4 of "CRM System"
    Then the state lists the fields "name, description, isLogical, metatypeId, lifecycleStatus, owner"
    And the state lists "lifecycleStatus" as "active" and "owner" without value

  @US-100 @AC1 @REQ-100
  Scenario: Value of an older version
    Given the history entity "CRM System" with 5 versions by "Kurt"
    And the public property "lifecycleStatus"
    And the values of "lifecycleStatus" of "CRM System" by version are "planned, planned, planned, active, active"
    When the enterprise architect reads the state of version 3 of "CRM System"
    Then the state lists "lifecycleStatus" as "planned"
    When the enterprise architect reads the state of version 4 of "CRM System"
    Then the state lists "lifecycleStatus" as "active"

  @US-100 @REQ-100
  Scenario Outline: Version boundaries
    Given the history entity "CRM System" with 5 versions by "Kurt"
    When the enterprise architect reads the state of version <version> of "CRM System"
    Then the version request is answered with <status>

    Examples:
      | version | status |
      | 1       | 200    |
      | 5       | 200    |
      | 6       | 404    |
      | 0       | 400    |

  @US-100 @AC1 @REQ-100
  Scenario: Mapping effective after the version
    Given the history entity "CRM System" of the subtype "Versioned Component" of "Application Component" with 5 versions by "Kurt"
    And the public property "owner"
    And "owner" is mapped to the metatype of "CRM System" from "2026-11-03"
    When the enterprise architect reads the state of version 4 of "CRM System"
    Then the state does not list "owner"

  # Requirement: Diff of a version; Comparison of two versions

  @US-099 @AC1 @AC2 @REQ-099
  Scenario: Only changed fields
    Given the history entity "CRM System" with 5 versions by "Kurt"
    And the public property "owner"
    And the public property "lifecycleStatus"
    And the values of "owner" of "CRM System" by version are "Kurt, Kurt, Kurt, Kurt, Michael"
    And the values of "lifecycleStatus" of "CRM System" by version are "active, active, active, active, active"
    When the enterprise architect reads the diff of version 5 of "CRM System"
    Then the diff lists "owner: Kurt → Michael"

  @US-099 @AC1 @REQ-099
  Scenario: Added and removed values
    Given the history entity "CRM System" with 3 versions by "Kurt"
    And the public property "lifecycleStatus"
    And the values of "description" of "CRM System" by version are "-, -, Core CRM"
    And the values of "lifecycleStatus" of "CRM System" by version are "active, active, -"
    When the enterprise architect reads the diff of version 3 of "CRM System"
    Then the diff lists "description: - → Core CRM, lifecycleStatus: active → -"

  @US-099 @AC3 @REQ-099
  Scenario: Net diff across versions
    Given the history entity "CRM System" with 7 versions by "Kurt"
    And the public property "owner"
    And the public property "lifecycleStatus"
    And the values of "owner" of "CRM System" by version are "Kurt, Kurt, Michael, Michael, Michael, Michael, Michael"
    And the values of "lifecycleStatus" of "CRM System" by version are "planned, planned, planned, active, active, planned, planned"
    When the enterprise architect compares version 2 with version 7 of "CRM System"
    Then the diff lists "owner: Kurt → Michael"

  @US-099 @AC3 @REQ-099
  Scenario Outline: Version range boundaries
    Given the history entity "CRM System" with 7 versions by "Kurt"
    When the enterprise architect compares version <from> with version <to> of "CRM System"
    Then the version request is answered with <status>

    Examples:
      | from | to | status |
      | 1    | 7  | 200    |
      | 6    | 7  | 200    |
      | 7    | 7  | 400    |
      | 0    | 7  | 400    |
      | 1    | 8  | 404    |

  # Requirement: Values the reader may not read

  @REQ-099 @REQ-160
  Scenario: Restricted value in the diff
    Given the history entity "CRM System" with 3 versions by "Kurt"
    And the property "investmentCost" readable only by "enterprise-architect"
    And the values of "investmentCost" of "CRM System" by version are "50, 100, 100"
    When the viewer reads the diff of version 2 of "CRM System"
    Then the diff lists "investmentCost (restricted)"

  @REQ-100 @REQ-160
  Scenario: Hidden in the complete state
    Given the history entity "CRM System" with 3 versions by "Kurt"
    And the property "investmentCost" readable only by "enterprise-architect"
    And the values of "investmentCost" of "CRM System" by version are "50, 100, 100"
    And the history visibility of "viewer" for "Application Component" is "hide"
    When the viewer reads the state of version 3 of "CRM System"
    Then the state does not list "investmentCost"

  # Requirement: Override reads of version values

  @REQ-128 @REQ-160
  Scenario: Read through the override role
    Given the history entity "CRM System" with 3 versions by "Kurt"
    And the property "investmentCost" readable only by "enterprise-architect"
    And the values of "investmentCost" of "CRM System" by version are "50, 100, 100"
    And the role "viewer" is on the override role list
    When the viewer reads the diff of version 2 of "CRM System"
    Then the diff lists "investmentCost: 50 → 100"
    And one "property-override-access" event with the channel "history" names "investmentCost" and "CRM System"

  @REQ-128
  Scenario: Read through a read role
    Given the history entity "CRM System" with 3 versions by "Kurt"
    And the property "investmentCost" readable only by "enterprise-architect"
    And the values of "investmentCost" of "CRM System" by version are "50, 100, 100"
    When the enterprise architect reads the diff of version 2 of "CRM System"
    Then the diff lists "investmentCost: 50 → 100"
    And no "property-override-access" event names "CRM System"

  # Requirement: Immutable versions

  @US-100 @AC2 @REQ-100
  Scenario: Delete rejected
    Given the history entity "CRM System" with 5 versions by "Kurt"
    When the enterprise architect sends "DELETE" to version 4 of "CRM System"
    Then the version request is answered with 405 allowing "GET"
    And the state of version 4 of "CRM System" is unchanged

  @US-100 @AC3 @REQ-100
  Scenario: Edit rejected
    Given the history entity "CRM System" with 5 versions by "Kurt"
    When the enterprise architect sends "PUT" to version 4 of "CRM System"
    Then the version request is answered with 405 allowing "GET"
    And the state of version 4 of "CRM System" is unchanged

  @REQ-100
  Scenario: Without permission
    Given the history entity "CRM System" with 2 versions by "Kurt"
    When the portal reader reads the state of version 1 of "CRM System"
    Then the version request is answered with 403
