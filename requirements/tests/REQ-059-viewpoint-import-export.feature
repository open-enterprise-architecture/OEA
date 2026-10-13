# Derived from: requirements/req/req-059-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-04
Feature: REQ-059 – Import and export viewpoint

  The system MUST enable persons with metamodel editor permission to
  export individual viewpoints as a YAML file and to import viewpoint
  YAML files; on import, the system MUST validate all referenced
  EntityType IDs (`allowedEntityTypes`, `allowedConnectionTypes`,
  `notationMappings.entityTypeId`) against the target
  MetamodelConfiguration and report missing IDs as errors before the
  import is aborted.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Export single viewpoint – user-defined viewpoint `cloud-security-view...
    Given user-defined viewpoint `cloud-security-view` with 4 allowedEntityTypes, 3 notationMappings
    When  Kurt exports this viewpoint as YAML
    Then  YAML contains all fields incl. notationMappings with defaultWidth/defaultHeight; ID `cloud-security-view` is included

  @AC2
  Scenario: AC2 – Roundtrip – Kurt imports the exported YAML file into another instanc...
    Given the precondition according to the requirement is met
    When  Kurt imports the exported YAML file into another instance (all referenced EntityType IDs present)
    Then  HTTP 201; viewpoint is present in the target instance with identical configuration

  @AC3
  Scenario: AC3 – Missing EntityType IDs – YAML references `allowedEntityTypes: [securi...
    Given YAML references `allowedEntityTypes: [security-zone]`; target instance does not know EntityType ID `security-zone`
    When  Kurt imports the file
    Then  HTTP 422; error message contains `unknown entityTypeId: security-zone`; no viewpoint created

  @AC4
  Scenario: AC4 – ID conflict – target instance already has a viewpoint with ID `cloud-security-view`
    Given target instance already has a viewpoint with ID `cloud-security-view`
    When  Kurt uploads a viewpoint file with the same ID
    Then  HTTP 409; response contains a suggested alternative ID (e.g. `cloud-security-view-2`); no import without explicit confirmation

  @AC5
  Scenario: AC5 – System-defined not importable – YAML with `viewpointType: system-defined`
    Given YAML with `viewpointType: system-defined`
    When  Kurt imports the file
    Then  system ignores `viewpointType=system-defined`; imports the viewpoint as `user-defined`; HTTP 201

  @AC6
  Scenario: AC6 – Bulk export – Kurt exports all user-defined viewpoints
    Given the precondition according to the requirement is met
    When  Kurt exports all user-defined viewpoints
    Then  ZIP download with one YAML file per user-defined viewpoint; system-defined viewpoints included (optional, as helpful for roundtrip)
