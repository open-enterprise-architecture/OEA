# Spec: openspec repository/entity-delete
# Source material: requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md; REQ-146, REQ-159; UC-10; ADR-019; ADR-032

@US-074 @UC-10 @repository
Feature: Delete and restore an entity with its cascade

  An architect deletes an entity from the repository: the entity, its
  children on all levels and every connection of one of them are deleted
  together, and a restore brings back exactly this cascade. The preview
  names what a deletion would take along.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP
    And the deletion entities "ERP", "ERP Finance", "ERP Logistics", "Warehouse" and "CRM"
    And "ERP Finance" and "ERP Logistics" are children of "ERP"
    And "Warehouse" is a child of "ERP Logistics"
    And "ERP" is connected to "CRM"

  # Requirement: Preview of a deletion

  @REQ-146
  Scenario: Entity with children and connections
    When the architect reads the deletion preview of "ERP"
    Then the preview names 3 children and 4 connections
    And nothing is deleted

  @REQ-146
  Scenario: Deleted entity has no preview
    Given the architect has deleted "CRM"
    When the architect reads the deletion preview of "CRM"
    Then the deletion answer is 404

  # Requirement: Delete an entity

  @REQ-146
  Scenario: Deleted with children and connections
    When the architect deletes "ERP" with its current version
    Then the deletion answer is 200 listing 8 IDs
    And "ERP", its 3 children and its 4 connections are deleted
    And "CRM" is not deleted
    And the audit records entity.deleted for "ERP" and entity.cascade-deleted naming "ERP" for the 7 others

  @REQ-146
  Scenario: Connection deleted alone
    When the architect deletes the connection "ERP to CRM" with its current version
    Then only the connection "ERP to CRM" is deleted

  @REQ-146
  Scenario: Stale version
    When the architect deletes "ERP" with the version 7 and then without If-Match
    Then the first deletion is answered with 412 and the second with 428
    And nothing is deleted

  @REQ-159
  Scenario: Without entity:delete
    When the viewer deletes "ERP" with its current version
    Then the deletion answer is 403
    And nothing is deleted

  @REQ-146
  Scenario: Structuring aid
    Given the deletion structuring aid "Finance landscape"
    When the architect deletes the structuring aid "Finance landscape" through the entity operation
    Then the deletion answer is 422
    And the structuring aid "Finance landscape" is not deleted

  @REQ-146
  Scenario: Already deleted
    Given the architect has deleted "ERP"
    When the architect deletes "ERP" with the version 1
    Then the deletion answer is 404

  # Requirement: Restore a deleted entity

  @REQ-159
  Scenario: Restored with its cascade
    Given the architect has deleted "ERP"
    When the architect restores the deleted "ERP"
    Then the deletion answer is 200
    And "ERP", its 3 children and its 4 connections are active again with their versions unchanged
    And the audit records entity.undeleted for each of the 8

  Scenario: Other endpoint still deleted
    Given the architect has deleted "ERP"
    And the architect has deleted "CRM"
    When the architect restores the deleted "ERP"
    Then the connection "ERP to CRM" stays deleted and is named with the reason "endpoint-deleted"
    And "CRM" is still deleted

  Scenario: Restoring a child alone
    Given the architect has deleted "ERP"
    When the architect restores the deleted "ERP Logistics"
    Then "ERP Logistics" and "Warehouse" are active again
    And "ERP Logistics" is at the top level as a new version and named as moved
    And "ERP" and "ERP Finance" stay deleted

  Scenario: Not deleted
    When the architect restores the deleted "CRM"
    Then the deletion answer is 409

  @REQ-159
  Scenario: Without entity:restore
    Given the architect has deleted "ERP"
    When the viewer restores the deleted "ERP"
    Then the deletion answer is 403
    And "ERP" is still deleted

  # Requirement: Version restore needs an active entity

  Scenario: Version restore of a deleted entity
    Given the architect has changed the description of "ERP"
    And the architect has deleted "ERP"
    When the architect restores the version 1 of "ERP"
    Then the deletion answer is 409 of type "entity-deleted"
    And "ERP" has the version 2

  Scenario: Version restore after the undelete
    Given the architect has changed the description of "ERP"
    And the architect has deleted "ERP"
    And the architect has restored the deleted "ERP"
    When the architect restores the version 1 of "ERP"
    Then the deletion answer is 200
    And "ERP" has the version 3
