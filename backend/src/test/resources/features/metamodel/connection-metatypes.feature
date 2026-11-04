# Spec: openspec metamodel/connection-metatypes, metamodel/metatype-editing
# Source material: requirements/user-stories/US-036-create-connection-entitytype.md, REQ-036, US-032 AC1

@US-036 @UC-04 @metamodel
Feature: Connection metatypes

  A connection metatype connects a source and a target entity. It may restrict the
  allowed metatypes of both endpoints; an allowed metatype also allows its
  subtypes, and a connection metatype may itself be an endpoint.

  Background:
    Given a running repository instance with a metamodel administrator

  Scenario: DataFlow between applications
    When the metamodel administrator creates the connection metatype "DataFlow" with "Application Component" and "DataFlow" as allowed sources and targets
    Then "DataFlow" is a connection metatype with "Application Component" and "DataFlow" as allowed sources and targets

  @US-032 @AC1
  Scenario: Relation as connection metatype
    Given the metatype "SecurityZone" and the connection metatype "Contains" from "SecurityZone" to "Technology Component"
    When the architect connects a "SecurityZone" to a "Technology Component" with "Contains"
    Then the connection is created

  Scenario: Connection flag fixed
    Given the metatype "SecurityZone"
    When the metamodel administrator makes "SecurityZone" a connection metatype
    Then the connection metatype change is rejected with 422

  Scenario: Subtype as source
    Given the metatype "SecurityZone" and the connection metatype "Contains" from "SecurityZone" to "Technology Component"
    And the metatype "CloudZone" with the base type "SecurityZone"
    When the architect connects a "CloudZone" to a "Technology Component" with "Contains"
    Then the connection is created

  Scenario: Mixed base type
    Given the metatype "SecurityZone"
    When the metamodel administrator creates the connection metatype "DataFlow" with the base type "SecurityZone"
    Then the connection metatype change is rejected with 422

  @AC4
  Scenario: Imported connection metatype
    When the metamodel administrator imports a package with a connection metatype that allows one of its metatypes as source
    Then the imported metatype is a read-only connection metatype with that source

  Scenario: Direction of a data flow
    Given the connection metatype "DataFlow" with "Application Component" and "DataFlow" as allowed sources and targets
    When a client asks for the allowed connections from "Application Component" to "Application Component"
    Then the answer allows "DataFlow" with the direction "bidirectional"
