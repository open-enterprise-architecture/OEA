# Derived from: requirements/req/req-037-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-04
Feature: REQ-037 – Solution-specific metamodel extension

  The system MUST make it possible to create, for a
  [Solution](../../business-objects/solution.md), its own
  `MetamodelConfiguration` with `scope=solution` that inherits the
  instance default metamodel and extends it with additional
  EntityTypes, stereotypes, and constraint rules; the solution
  extension MUST have its own `editMode`, configured independently of
  the `editMode` of the instance configuration; solution-specific
  types MUST NOT override or remove instance types.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – instance metamodel with `editMode=import-only`; solution "Cloud Exper...
    Given instance metamodel with `editMode=import-only`; solution "Cloud Experiment" without extension configuration
    When  Kurt creates a new extension configuration with `editMode=gui-and-import` for "Cloud Experiment" and defines `CloudService` in it
    Then  `CloudService` is available in the effective metamodel of "Cloud Experiment"; the instance default metamodel remains unchanged and locked; `CloudService` does not appear in other solutions

  @AC2
  Scenario: AC2 – solution "Cloud Experiment" with `CloudService` (solution type) and i...
    Given solution "Cloud Experiment" with `CloudService` (solution type) and instance type `ApplicationComponent`
    When  an architect creates a new entity within "Cloud Experiment"
    Then  both types are available for selection: `ApplicationComponent` (from instance) and `CloudService` (solution-specific)

  @AC3
  Scenario: AC3 – solution extension with `editMode=gui-and-import`
    Given solution extension with `editMode=gui-and-import`
    When  Kurt adds a new type via the GUI
    Then  this is possible although the instance metamodel is `import-only` (independent `editMode` levels)

  @AC4
  Scenario: AC4 – solution extension configuration is to define a type `ApplicationComp...
    Given solution extension configuration is to define a type `ApplicationComponent` (already present as an instance type)
    When  Kurt saves
    Then  error "Type 'ApplicationComponent' is already defined in the instance metamodel" appears; no entry created

  @AC5
  Scenario: AC5 – solution has no extension configuration (`metamodelExtensionId=null`)
    Given solution has no extension configuration (`metamodelExtensionId=null`)
    When  an architect creates an entity within this solution
    Then  only instance types are available; no error

  @AC6
  Scenario: AC6 – solution extension with `editMode=import-only`
    Given solution extension with `editMode=import-only`
    When  Kurt updates the solution extension via a YAML file (REQ-033)
    Then  the import is possible; GUI editing of this extension is blocked
