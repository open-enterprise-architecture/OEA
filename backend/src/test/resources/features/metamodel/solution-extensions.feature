# Spec: openspec metamodel/solution-extensions
# Source material: requirements/user-stories/US-037-try-out-architecture-metamodel.md, REQ-037

@US-037 @UC-04 @metamodel
Feature: Solution metamodel extensions

  An extension belongs to a solution and inherits the instance metamodel. Architects
  try out new metatypes in it without touching the instance metamodel; a name of the
  instance cannot be redefined. Until solutions exist, the metatypes of an extension
  are not used for entities.

  Background:
    Given a running repository instance with a metamodel administrator

  @AC1
  Scenario: Extension for Cloud Migration 2027
    When the architect creates an extension for the solution "Cloud Migration 2027" in edit mode "gui-and-import"
    Then the configurations list the extension of "Cloud Migration 2027" with the instance configuration as parent

  Scenario: Without permission
    When the viewer creates an extension for the solution "Cloud Migration 2027"
    Then the extension is rejected with 403

  @AC1
  Scenario: CloudService only in the extension
    Given an extension for the solution "Cloud Migration 2027"
    When the architect creates "CloudService" in the extension
    Then "CloudService" is part of the effective metamodel of the extension
    And the metatypes of the instance do not list "CloudService"

  @AC3
  Scenario: Instance type redefined
    Given an extension for the solution "Cloud Migration 2027"
    When the architect creates "Application Component" in the extension
    Then the metatype is rejected with 409 "name-taken"
    And the extension has no metatypes

  @AC4
  Scenario: Two architects maintain one extension
    Given an extension for the solution "Cloud Migration 2027"
    When the architect and the metamodel administrator each create a metatype in the extension
    Then both metatypes are part of the extension and each creation is audited with its author

  @AC2
  Scenario: Both groups in the effective metamodel
    Given an extension for the solution "Cloud Migration 2027" with "CloudService"
    When a client reads the metatypes of the extension
    Then the answer contains "Application Component" with the instance configuration and "CloudService" with the extension

  Scenario: Entity of an extension metatype
    Given an extension for the solution "Cloud Migration 2027" with "CloudService"
    When the architect writes an entity of "CloudService" through the batch
    Then the entity is rejected with "METATYPE_NOT_FOUND"
