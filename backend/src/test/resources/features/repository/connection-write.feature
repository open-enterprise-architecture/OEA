# Spec: openspec repository/connection-write, repository/entity-batch-write
# Source material: requirements/user-stories/US-036-create-connection-entitytype.md, REQ-036, US-141 AC2

@US-036 @UC-04 @repository
Feature: Writing connections

  Connections are entities of a connection metatype with a fixed source and
  target. They are written with createConnection or in the batch, after the
  entities of the same batch, and their endpoints are checked against the
  connection metatype.

  Background:
    Given a running repository instance with a metamodel administrator
    And the connection metatype "DataFlow" with "Application Component" and "DataFlow" as allowed sources and targets

  @AC1
  Scenario: DataFlow without source
    Given the applications "AppB"
    When the architect writes a "DataFlow" to "AppB" without a source
    Then the connection is rejected with the code "SOURCE_REQUIRED"

  @AC3
  Scenario: Connection as source not allowed
    Given the applications "AppA", "AppB" and "AppC" and the data flow "DF-1" from "AppA" to "AppB"
    And the connection metatype "AppLink" that allows only "Application Component" as source
    When the architect writes an "AppLink" from "DF-1" to "AppC"
    Then the connection is rejected with the code "SOURCE_NOT_ALLOWED"

  Scenario: Unknown target
    Given the applications "AppA"
    When the architect writes a "DataFlow" from "AppA" to an unknown target
    Then the connection is rejected with the code "TARGET_NOT_FOUND"

  @AC2
  Scenario: Branching data flow
    Given the applications "AppA", "AppB" and "AppC" and the data flow "DF-1" from "AppA" to "AppB"
    When the architect writes the data flow "DF-2" from "DF-1" to "AppC"
    Then "DF-2" is stored with the source "DF-1"

  @US-141 @AC2
  Scenario: Entities and connections in one batch
    When the architect sends a batch with the applications "AppA" and "AppB" and a data flow between them
    Then the batch creates 3 items and the connection has the source "AppA"

  Scenario: Upsert of an existing connection
    Given the applications "AppA", "AppB" and "AppC" and the data flow "DF-1" from "AppA" to "AppB"
    When the data flow "DF-1" is sent again with the protocol "sftp"
    Then "DF-1" has version 2

  Scenario: Default name
    Given the applications "CRM" and "ERP"
    When the architect creates a "DataFlow" from "CRM" to "ERP" without a name
    Then the connection is named "CRM → ERP"

  Scenario: Other target on update
    Given the applications "AppA", "AppB" and "AppC" and the data flow "DF-1" from "AppA" to "AppB"
    When the data flow "DF-1" is sent again with the target "AppC"
    Then the connection is rejected with the code "ENDPOINT_CHANGE_NOT_SUPPORTED"
    And "DF-1" still has the target "AppB"

  @REQ-161 @AC1
  Scenario: Architect creates a scoping connection
    Given the connection metatype "Scoping DomainAssignment" is the scoping type of the property "Scoping Security Classification"
    And the elements "Scoping Michael" and "Scoping ERP"
    When the architect creates a "Scoping DomainAssignment" connection from "Scoping Michael" to "Scoping ERP"
    Then the connection is rejected with 403 and no "Scoping DomainAssignment" connection exists
