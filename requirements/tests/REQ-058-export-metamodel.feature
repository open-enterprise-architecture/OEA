# Derived from: requirements/req/req-058-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-04
Feature: REQ-058 – Export metamodel configuration

  The system MUST enable persons with metamodel editor permission to
  download the complete MetamodelConfiguration of their own instance
  as a YAML file or JSON file; the exported file MUST be usable as
  input for the import according to REQ-033 of another OEA instance.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Export as YAML – Kurt has metamodel editor permission; instance has 5...
    Given Kurt has metamodel editor permission; instance has 5 custom EntityTypes, 2 ArchitectureLayers, 3 Viewpoints
    When  Kurt exports as YAML
    Then  download starts; YAML contains all 5 custom EntityTypes, 2 ArchitectureLayers, 3 Viewpoints with notationMappings incl. defaultWidth/defaultHeight

  @AC2
  Scenario: AC2 – Export as JSON – same configuration as AC1
    Given same configuration as AC1
    When  Kurt exports as JSON
    Then  download starts; JSON is structurally equivalent to the YAML version

  @AC3
  Scenario: AC3 – Roundtrip compatibility – Kurt imports the exported YAML file into a...
    Given the precondition according to the requirement is met
    When  Kurt imports the exported YAML file into another OEA instance via REQ-033
    Then  all non-built-in types are imported correctly (diff shows: X new types, 0 syntax errors)

  @AC4
  Scenario: AC4 – Without permission – Franz has no metamodel editor permission
    Given Franz has no metamodel editor permission
    When  Franz calls the export endpoint
    Then  HTTP 403; no download

  @AC5
  Scenario: AC5 – includeBuiltins=false – Kurt exports with `includeBuiltins=false`
    Given the precondition according to the requirement is met
    When  Kurt exports with `includeBuiltins=false`
    Then  exported file contains only custom EntityTypes and user-defined configuration elements; no built-in EntityTypes
