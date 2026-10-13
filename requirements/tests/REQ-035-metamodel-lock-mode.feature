# Derived from: requirements/req/req-035-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-04
Feature: REQ-035 – Metamodel lock mode (import-only)

  The operator MUST be able to put the `MetamodelConfiguration` of an
  instance into `import-only` mode; in this mode, create, update, and
  delete operations on EntityTypeDefinitions, Stereotypes, and
  ConstraintRules MUST NOT be executed via the GUI API – regardless of
  the requesting person's permission; changes are possible exclusively
  via the import path (REQ-033).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – `editMode=import-only`
    Given `editMode=import-only`
    When  Kurt tries to create a new EntityType via the GUI (REQ-032)
    Then  the request is rejected with 403 and a pointer to the import path; the MetamodelConfiguration remains unchanged

  @AC2
  Scenario: AC2 – `editMode=import-only`
    Given `editMode=import-only`
    When  Kurt uploads and confirms a YAML file via import (REQ-033)
    Then  the import is processed normally; new EntityTypes are in the configuration

  @AC3
  Scenario: AC3 – `editMode=import-only`
    Given `editMode=import-only`
    When  Kurt opens the MetamodelConfiguration overview
    Then  he sees all EntityTypes (read-only); create/edit/delete buttons are missing or disabled; a notice explains the lock mode

  @AC4
  Scenario: AC4 – `editMode=gui-and-import` (default)
    Given `editMode=gui-and-import` (default)
    When  no explicit configuration was made
    Then  GUI and import editing are both active (no difference from REQ-032/REQ-033 behavior)

  @AC5
  Scenario: AC5 – operator changes `editMode` from `import-only` to `gui-and-import`
    Given operator changes `editMode` from `import-only` to `gui-and-import`
    When  Kurt subsequently creates an EntityType via the GUI
    Then  this is possible again; no restart necessary (configuration is evaluated at runtime)
