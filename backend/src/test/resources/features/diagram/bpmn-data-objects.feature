# Spec: openspec diagram/bpmn-data-objects (us-083-bpmn-data-objects)
# Source material: requirements/user-stories/US-083-bpmn-dataobject-enrichment.md, US-074; REQ-081 AC1-AC5, AC7; UC-10
# The scenarios share one instance of their own (the imports change its metamodel) and run in file order.
# The editor scenarios (symbols, drag, tags, context menu, badges, no nesting) are covered by the frontend specs and
# stories; this feature covers the API.

@US-083 @US-074 @REQ-081 @UC-10 @diagram @bpmn
Feature: Data objects and data stores in BPMN diagrams

  A data object or a data store is a symbol of an entity. A data input association leads from it to a
  task, a data output association from a task to it and records what the task does with the data.

  Background:
    Given an instance of its own with the TOGAF, the BPMN and the organization package

  @AC3 @AC4 @REQ-081 @AC2 @AC3
  Scenario: Input and output association with a transformation
    Given a process diagram "Order handling" with the data object "Order" and the task "Review order"
    When the enterprise architect saves the input association from "Order" to "Review order"
    And the enterprise architect saves the output association from "Review order" to "Order" with the transformation "enrich" and the attributes "status" and "reviewedBy"
    Then the data save is accepted
    And the output association has the transformation "enrich" and the attributes "status" and "reviewedBy" on two lines
    And the data diagram shows an edge from "Order" to "Review order" and one from "Review order" to "Order"

  @AC3 @REQ-081 @AC5
  Scenario: Wrong direction
    Given a process diagram "Order handling" with the data object "Order" and the task "Review order"
    When the enterprise architect saves the input association from "Review order" to "Order"
    Then the data save is rejected with 422 "DATA_ASSOCIATION_DIRECTION" naming the new connection
    And the data diagram has no edge

  @AC5 @REQ-081 @AC5
  Scenario: Realized by from a data store
    Given a process diagram "Order storage" with the data store "Order DB"
    And the system software "PostgreSQL"
    When the enterprise architect sets "PostgreSQL" as the realizing element of "Order DB" and saves
    Then the data save is accepted
    And "Order DB" shows the badge "PostgreSQL" and the creation is audited
    And the data diagram has no shape for "PostgreSQL"

  @AC5 @REQ-081 @AC5
  Scenario: Remove the realizing element
    Given a process diagram "Order storage" with the data store "Order DB"
    And the system software "PostgreSQL"
    And the enterprise architect set "PostgreSQL" as the realizing element of "Order DB"
    When the enterprise architect removes the realizing element of "Order DB" and saves
    Then the data save is accepted
    And "Order DB" shows no badge
    And the removed realizing connection is restorable and audited as deleted

  @AC7 @REQ-081 @AC7
  Scenario: Catalog of data objects with the tasks that enrich them
    Given a process diagram "Invoicing" with the data objects "Order" and "Invoice" and the task "Review order"
    And the enterprise architect saved the output association from "Review order" to "Order" with the transformation "enrich" and the attributes "status" and "reviewedBy"
    And a catalog "Data and tasks" of data objects with the inbound join "Data Output Association" to the tasks
    Then the data object "Order" shows the task "Review order" in the join column
    And the data object "Invoice" shows an empty cell in the join column
