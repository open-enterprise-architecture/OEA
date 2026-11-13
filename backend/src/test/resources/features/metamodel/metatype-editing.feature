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

  # Parallel-change setting of a metatype (US-044, owner decision 2026-11-13)

  @US-044
  Scenario: Turning the setting on for a shared metatype
    Given the setting of the shared metatype "Application Component" is off
    When the metamodel administrator turns on parallel changes in the baseline for "Application Component"
    Then "Application Component" shows parallel changes in the baseline with a version raised by 1
    And the metamodel changes list the setting of "Application Component" under the kind "metatype" by the metamodel administrator

  @US-044
  Scenario: Default off
    When the metamodel administrator creates the metatype "Parallel Default Zone"
    Then the metatype "Parallel Default Zone" does not show parallel changes in the baseline

  @US-044
  Scenario: Setting without metamodel administration
    Given the organization metatype "Parallel Zone"
    When the architect turns on parallel changes in the baseline for "Parallel Zone"
    Then the setting is rejected with 403 and "Parallel Zone" is unchanged

  @US-044
  Scenario: Setting with an outdated version
    Given the organization metatype "Parallel Zone"
    When the metamodel administrator turns on parallel changes in the baseline for "Parallel Zone" with an outdated version
    Then the setting is rejected with 412 and "Parallel Zone" is unchanged

  @US-044
  Scenario: Re-import keeps the setting
    Given the package "parallel-reimport" with one metatype
    And the metamodel administrator turned on parallel changes in the baseline for "parallel-reimport"
    When the metamodel administrator imports the package "parallel-reimport" again
    Then "parallel-reimport" still shows parallel changes in the baseline

  @US-044
  Scenario: Setting not inherited
    Given the organization metatype "Parallel Base"
    And the metamodel administrator turned on parallel changes in the baseline for "Parallel Base"
    And the subtype "Parallel Sub" of "Parallel Base" with the entity "Sub-CRM" that another solution retires
    When the architect reads the entry "Sub-CRM" in the baseline of a solution
    Then the entry names no other solution
