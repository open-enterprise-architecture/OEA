# Derived from: requirements/req/req-060-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-08
Feature: REQ-060 – Create and manage data object (data-object) as an entity

  The system MUST enable persons with entity editor permission to
  create, edit, and delete ArchitectureEntities with the EntityType
  `data-object`; on creation, the system MUST automatically assign an
  instance-wide unique integer ID and set `entityTypeId=data-object`
  as an immutable meta-type binding. For `data-object`, the
  EntityType in the metamodel MUST support the following
  PropertyDefinitions: `dataClassification` (enum, mandatory),
  `personalDataCategories` (varchar, optional), `dataOwner` (varchar,
  warning).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Creation with ID assignment – Lukas creates entity `CustomerMaster` (entityTypeId=`data-object`)
    Given the precondition according to the requirement is met
    When  Lukas creates entity `CustomerMaster` (entityTypeId=`data-object`)
    Then  HTTP 201; `id` is integer ≥ 1; `entityTypeId=data-object` is set; ID is unique in the instance

  @AC2
  Scenario: AC2 – Mandatory property enforces value – `dataClassification` is mandatory
    Given `dataClassification` is mandatory
    When  Lukas creates entity without `dataClassification`
    Then  HTTP 422; error message names `dataClassification` as missing

  @AC3
  Scenario: AC3 – Warning property saves anyway – `dataOwner` is warning
    Given `dataOwner` is warning
    When  Lukas creates entity without `dataOwner`
    Then  HTTP 200; entity saved; response contains `warnings: ["dataOwner: value missing"]`

  @AC4
  Scenario: AC4 – entityTypeId immutable – entity with id=42 and entityTypeId=`data-object` exists
    Given entity with id=42 and entityTypeId=`data-object` exists
    When  Lukas tries to set `entityTypeId=application-component` via PUT
    Then  HTTP 422; `entityTypeId` was not changed

  @AC5
  Scenario: AC5 – Deletion shows reference warning – entity id=42 is referenced by carries-data connection id=103
    Given entity id=42 is referenced by carries-data connection id=103
    When  Lukas deletes entity id=42
    Then  system warns about referencing connections; after confirmation: entity deleted; carries-data connection id=103 gets `targetEntityId=null` or is also deleted (cascade TBD in ADR)
