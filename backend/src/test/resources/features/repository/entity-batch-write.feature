# Spec: openspec repository/entity-batch-write
# Source material: requirements/user-stories/US-141-entity-batch-import.md, REQ-153, REQ-154; ADR-026

@US-141 @UC-22 @repository
Feature: Batch write of entities

  External systems and import scripts populate the repository through the
  batch endpoint. Entities are identified by their external ID: a repeated
  run updates instead of duplicating. A batch is either written item by item
  with a result per item, or atomically.

  Background:
    Given an instance with the starter package, an enterprise architect and a viewer logged in through the IdP

  @ADR-032
  Scenario: Batch without permission
    When the viewer sends a batch with 3 "Application Component" entities with the prefix "forbidden"
    Then the batch is rejected with 403
    And no entity with the prefix "forbidden" exists

  @REQ-154 @AC4
  Scenario: Batch above the limit
    When the enterprise architect sends a batch with 501 "Application Component" entities with the prefix "limit"
    Then the batch is rejected with 422
    And no entity with the prefix "limit" exists

  @US-141 @AC1
  Scenario: Upsert of a repository scan
    Given 50 "Application Component" entities with the prefix "scan" and the name "Old" exist
    When the enterprise architect sends a batch with 200 "Application Component" entities with the prefix "scan"
    Then the summary reports 150 created and 50 updated
    And 200 entities with the prefix "scan" exist, none named "Old"

  @US-141 @AC1
  Scenario: Repeated upsert
    Given 20 "Application Component" entities with the prefix "repeat" exist
    When the enterprise architect sends a batch with 20 "Application Component" entities with the prefix "repeat"
    Then the summary reports 20 unchanged and 0 created

  Scenario: Partial update by a source
    Given an "Application Component" "Intranet" with external ID "intranet:partial" and "Status" "active" exists
    When the enterprise architect writes "Intranet Portal" with external ID "intranet:partial" without properties
    Then "intranet:partial" is named "Intranet Portal" and keeps the "Status" "active"

  Scenario: Duplicate externalId in a batch
    When the enterprise architect sends a batch with the external ID "twice:1" twice
    Then the first item is created and the second is rejected with the code "DUPLICATE_EXTERNAL_ID"

  Scenario: Create-only with an existing externalId
    Given an "Application Component" "Original" with external ID "create-only:1" exists
    When the enterprise architect writes "Changed" with external ID "create-only:1" with strategy "create-only"
    Then the item is rejected with the code "EXTERNAL_ID_EXISTS" for the field "externalId"
    And "create-only:1" is named "Original"

  Scenario: Skip-existing
    Given an "Application Component" "Existing" with external ID "skip:1" exists
    When the enterprise architect sends "skip:1" and the new "skip:2" with strategy "skip-existing"
    Then "skip:1" is reported as "skipped" and "skip:2" as "created"
    And "skip:1" is named "Existing"

  @REQ-154 @AC1
  Scenario: Partial errors without atomicity
    When the enterprise architect sends 100 new "Checked Type" entities with the prefix "partial", 5 of them without "Version"
    Then the batch is answered with 200, 95 created and 5 errors with the code "MANDATORY_MISSING"

  @REQ-154
  Scenario: Error in an atomic batch
    When the enterprise architect sends 100 new "Checked Type" entities with the prefix "atomic", 5 of them without "Version", atomically
    Then the batch is rejected with 422 listing 5 errors with their item index
    And no entity with the prefix "atomic" exists

  Scenario: Batch with connections
    When the enterprise architect sends a batch with one entity "connections:1" and one connection
    Then the batch is rejected with 422
    And no entity with the prefix "connections" exists
