# Spec: openspec metamodel/metatype-editing, metamodel/property-mappings, repository/entity-write
# Source material: requirements/user-stories/US-032-create-entitytype.md, US-137, REQ-032, REQ-148, REQ-149

@US-032 @UC-04 @metamodel
Feature: Editing the metamodel

  Metamodel administrators create organization metatypes and properties through
  the API; they are usable for entities at once and every change is attributed to
  its author in the audit trail. Removing a property deletes its values; a new
  mandatory property marks the existing entities that miss it.

  Background:
    Given a running repository instance with a metamodel administrator

  @AC1 @AC3
  Scenario: SecurityZone created
    When the metamodel administrator creates the metatype "SecurityZone" with the mandatory enum property "Trust Level"
    Then "SecurityZone" is listed with scope "organization"
    And an architect can write a "SecurityZone" with "Trust Level" "high"
    And the audit trail attributes the creation of "SecurityZone" to the metamodel administrator

  @AC2
  Scenario: Name of an imported metatype
    When the metamodel administrator creates the metatype "application component"
    Then the metamodel change is rejected with 409 and problem type "name-taken"

  Scenario: Without permission
    When the architect creates the metatype "DataPipeline"
    Then the metamodel change is rejected with 403

  @REQ-032
  Scenario: Removing a property deletes its values
    Given the metatype "Network Zone" with the optional property "Cost Center" and 3 entities with a value
    When the metamodel administrator removes "Cost Center" from "Network Zone"
    Then no entity of "Network Zone" has a value for "Cost Center"
    And the history of each of the 3 entities holds the removed value

  @REQ-149
  Scenario: Existing entities marked by a new mandatory property
    Given the metatype "Data Zone" with 2 entities
    When the metamodel administrator maps the mandatory property "Classification" to "Data Zone"
    Then the response states 2 marked entities
    And writing one of them again without "Classification" is rejected with "MANDATORY_MISSING"

  @US-137 @AC1 @REQ-148
  Scenario: Mandatory from an effective date
    Given the metatype "Release Zone" with the property "Version" mapped as optional since 2026-01-01
    When the metamodel administrator maps "Version" to "Release Zone" as mandatory from 2026-07-01
    Then "Release Zone" has 2 entries for "Version"
    And "Version" is mandatory for "Release Zone" today

  @US-137 @AC2
  Scenario: Same effective date twice
    Given the metatype "Audit Zone" with the property "Version" mapped as optional since 2026-01-01
    When the metamodel administrator maps "Version" to "Audit Zone" as mandatory from 2026-01-01
    Then the metamodel change is rejected with 409 and problem type "conflict"
