# Derived from: requirements/req/req-033-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-04
Feature: REQ-033 – Import metamodel from file

  The system MUST enable persons with metamodel editor permission to
  upload a metamodel configuration file (YAML or JSON); the system
  MUST validate the file before import, show a preview of the changes
  (diff: new types, conflicts, unchanged types), and import only after
  explicit confirmation.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – a valid YAML file with 3 new custom EntityTypes
    Given a valid YAML file with 3 new custom EntityTypes
    When  Kurt uploads the file and confirms the preview
    Then  all 3 types are present in the MetamodelConfiguration; audit log contains 3 entries

  @AC2
  Scenario: AC2 – a YAML file that defines a type named `ApplicationComponent` (built-...
    Given a YAML file that defines a type named `ApplicationComponent` (built-in)
    When  Kurt uploads the file
    Then  the preview shows this entry as red/conflict with "not importable (built-in)"; it cannot be confirmed

  @AC3
  Scenario: AC3 – a syntactically invalid YAML file
    Given a syntactically invalid YAML file
    When  Kurt uploads it
    Then  an error message with line and column appears immediately; no preview

  @AC4
  Scenario: AC4 – a valid file with 2 new types and 1 conflicting (overwritable custom...
    Given a valid file with 2 new types and 1 conflicting (overwritable custom type)
    When  Kurt confirms only the 2 new types and skips the conflict
    Then  only the 2 new ones are imported; the existing custom type remains unchanged

  @AC5
  Scenario: AC5 – a JSON file with the same structure as the YAML variant
    Given a JSON file with the same structure as the YAML variant
    When  Kurt uploads it
    Then  it is handled the same way (format auto-detected)
