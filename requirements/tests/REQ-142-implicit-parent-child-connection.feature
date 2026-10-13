# Derived from: requirements/req/req-142-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: test (automated)

@functional @should @UC-13
Feature: REQ-142 – Implicit composition connection when nesting instantiated components

  When an instantiated ArchitectureEntity (`isStructuralGrouper=false`)
  is placed as a child of another instantiated entity, the system MUST automatically create an
  ArchiMate composition connection (`autoCreated=true`,
  `source=parentEntityId`, `target=childEntityId`). This connection
  MUST be visible in the "Connections" category of the solution.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Entity A and B (`isStructuralGrouper=false`); B.parentEntityId is set to ...
    Given Entity A and B (`isStructuralGrouper=false`); B.parentEntityId is set to A
    When  saving the change
    Then  composition connection (autoCreated=true) between A and B exists in "Connections"

  @AC2
  Scenario: AC2 – Structuring aid C (`isStructuralGrouper=true`) and Entity D
    Given structuring aid C (`isStructuralGrouper=true`) and Entity D
    When  D.parentEntityId = C is set
    Then  no connection is created

  @AC3
  Scenario: AC3 – AC1 state (autoCreated connection exists)
    Given AC1 state (autoCreated connection exists)
    When  B.parentEntityId set to null
    Then  autoCreated connection removed automatically

  @AC4
  Scenario: AC4 – autoCreated connection from AC1; user changes type to aggregation (`auto...
    Given autoCreated connection from AC1; user changes type to aggregation (`autoCreated=false`)
    When  B.parentEntityId set to null
    Then  aggregation connection is retained (autoCreated=false)
