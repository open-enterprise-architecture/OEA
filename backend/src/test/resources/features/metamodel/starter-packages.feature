# Spec: openspec metamodel/starter-packages, metamodel/metatype-catalog
# Source material: requirements/tests/REQ-112-*.feature; ADR-005

@US-033 @UC-04 @metamodel
Feature: Starter packages and the metatype catalog

  OEA ships starter packages that give a new instance its first entity types
  (ADR-005). The metamodel administrator sees them with a summary and imports
  one without uploading a file; afterwards everybody with read access sees the
  imported metatypes with their scope, origin and properties.

  Background:
    Given a new instance with a metamodel administrator, a viewer and a person without role logged in through the IdP

  @REQ-112 @AC1
  Scenario: Package list of a new instance
    When the metamodel administrator lists the starter packages
    Then "oea-starter-togaf-classic" is listed with 7 metatypes, among them "Application Component", "Data Object" and "Technology Component"
    And "oea-starter-togaf-classic" is listed as not imported

  @US-033 @AC1 @ADR-005
  Scenario: Starter configuration imported
    When the metamodel administrator imports the starter package "oea-starter-togaf-classic"
    Then 7 metatypes exist with scope "imported", each with the property "Status" and the values "planned, active, phasing-out, retired"
    And "oea-starter-togaf-classic" is listed as imported
    And the audit trail contains the starter import with 7 metatype imports under one batch ID

  Scenario: Unknown starter package
    When the metamodel administrator imports the starter package "does-not-exist"
    Then the request is rejected with 404

  @UC-04
  Scenario: Metatypes after the starter import
    Given the starter package "oea-starter-togaf-classic" has been imported
    When the viewer lists the metatypes
    Then 7 metatypes are returned with scope "imported" and origin "oea-starter-togaf-classic"
    And "Application Component" maps the property "Status" with 4 values

  @ADR-032
  Scenario: Reading without permission
    When the person without role lists the metatypes
    Then the request is rejected with 403
