# Derived from: requirements/req/req-036-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @must @UC-04
Feature: REQ-036 – Connection EntityType in the metamodel

  The system MUST make it possible to set the flag `isConnection=true`
  when creating a custom EntityType; a connection type MUST enforce
  exactly one `source` and one `target` reference on each of its
  instances; `source` and `target` MAY point to instances of any
  EntityType class – including other connection instances, which
  yields T-relationships; the allowed types for `source` and `target`
  MAY optionally be restricted.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – EntityType `DataFlow` with `isConnection=true`, `allowedSourceTypes=...
    Given EntityType `DataFlow` with `isConnection=true`, `allowedSourceTypes=[ApplicationComponent, DataFlow]`, `allowedTargetTypes=[ApplicationComponent, DataFlow]`
    When  Kurt creates a new `DataFlow` instance without specifying `source`
    Then  validation error "source is a mandatory field" appears; no object is saved

  @AC2
  Scenario: AC2 – `DataFlow` instance DF-1 (source=AppA, target=AppB) already exists
    Given `DataFlow` instance DF-1 (source=AppA, target=AppB) already exists
    When  Kurt creates a new `DataFlow` instance DF-2 with `source=DF-1` (connection → connection) and `target=AppC`
    Then  DF-2 is saved; in the graph DF-2 points to DF-1 (T-relationship: DF-2 branches off from DF-1, AppC is the endpoint)

  @AC3
  Scenario: AC3 – `DataFlow` has `allowedSourceTypes=[ApplicationComponent]` (DataFlow n...
    Given `DataFlow` has `allowedSourceTypes=[ApplicationComponent]` (DataFlow not in the list)
    When  Kurt wants to create DF-2 with `source=DF-1` (a connection instance)
    Then  validation error "DataFlow is not an allowed source type for DataFlow" appears

  @AC4
  Scenario: AC4 – EntityType `DataFlow` with `isConnection=true` and property `protocol...
    Given EntityType `DataFlow` with `isConnection=true` and property `protocol (enum)`
    When  Kurt creates an instance with `source=AppA`, `target=AppB`, `protocol=REST`
    Then  the instance is saved with all fields (own properties coexist with source/target)

  @AC5
  Scenario: AC5 – `isConnection=false` (standard EntityType `ApplicationComponent`)
    Given `isConnection=false` (standard EntityType `ApplicationComponent`)
    When  Kurt creates an `ApplicationComponent` instance
    Then  `source` and `target` are not mandatory fields; no change to existing behavior

  @AC6
  Scenario: AC6 – a YAML import file (REQ-033) defines `DataFlow` with `isConnection=true`
    Given a YAML import file (REQ-033) defines `DataFlow` with `isConnection=true`
    When  Kurt imports the file
    Then  `DataFlow` is correctly imported as a connection type; `isConnection=true` is visible in the GUI
