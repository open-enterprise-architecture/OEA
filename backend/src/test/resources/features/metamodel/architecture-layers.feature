# Spec: openspec metamodel/architecture-layers
# Source material: requirements/user-stories/US-057 AC4, REQ-053 BR-04, ADR-017; owner decision 2026-11-18

@US-057 @UC-04 @metamodel @architecture-layers
Feature: Architecture layers

  The metamodel holds a configurable list of architecture layers, which charts
  group by. Metamodel administrators maintain the layers of the organization,
  packages bring their own, and a metatype has at most one layer.

  Background:
    Given a repository instance with the metamodel administrator Sabine and the viewer Franz

  Scenario: Create a layer
    When Sabine creates the layer "Business"
    Then the layer "Business" is listed after the existing layers and the audit records "metamodel.layer-created" under the kind "layer"

  Scenario: Name at the limits
    When Sabine creates one layer with a name of 100 characters, one with 101 characters and one with only blanks
    Then the layer with 100 characters is created, and the others are rejected with 422 naming "name"

  Scenario: Name taken
    Given the layer "Business" exists
    When Sabine creates the layer "business"
    Then the layer request is rejected with 409 "name-taken" and no layer is created

  Scenario: Number of layers at the limit
    Given 19 layers exist
    When Sabine creates a layer, and then another one
    Then the 20th is created, and the 21st is rejected with 422

  Scenario: Reorder
    Given the layers "Business", "Application" and "Data" exist
    When Sabine moves "Data" before "Application"
    Then Franz sees "Data" before "Application"

  Scenario: Reader without metamodel:write
    When Franz creates the layer "Business"
    Then the layer request is rejected with 403 and no layer is created

  Scenario: Locked metamodel
    Given the layer "Strategy" exists
    And the instance metamodel is set to "import-only"
    When Sabine renames the layer "Strategy" to "Vision"
    Then the layer request is rejected with 403 "metamodel-locked" and the layer "Strategy" is unchanged

  Scenario: German label of an organization layer
    Given the layer "Strategy" exists
    When Sabine sets the German label "Strategie" for the layer "Strategy"
    Then the German label bundle carries "Strategie" under the key of the layer "Strategy"

  Scenario: Assign a layer
    Given the layer "Application" exists
    And the metatype "Business Application" without a layer
    When Sabine sets the layer "Application" on the metatype "Business Application"
    Then the metatype "Business Application" returns the layer "Application" and the change is audited as a metatype change

  Scenario: Unknown layer
    Given the metatype "Business Application" without a layer
    When Sabine sets a layer ID that names no layer on the metatype "Business Application"
    Then the layer request is rejected with 422 naming "architectureLayerId" and the metatype "Business Application" has no layer

  Scenario: Delete a layer in use
    Given the layer "Application" exists
    And "Application" is the layer of the metatypes "Business Application" and "Support Application"
    When Sabine deletes the layer "Application"
    Then the layer request is rejected with 409 "in-use" and the count 2, and the layer "Application" stays

  Scenario: Imported layer
    Given the starter package "oea-starter-togaf-classic" is installed
    When Sabine renames the layer "Technology" to "Tech"
    Then the layer request is rejected with 409 "read-only-metatype" and the layer "Technology" is unchanged

  Scenario: Starter package with layers
    When Sabine imports the starter package "oea-starter-togaf-classic"
    Then the layers Business, Data, Application and Technology exist as imported, "Application Component" has the layer "Application" and "Data Object" the layer "Data"

  Scenario: Reference to an unknown layer
    When Sabine imports a package whose metatype references a layer that is not in the package
    Then the import is rejected with 422 and nothing is imported

  Scenario: Name of an organization layer
    Given the layer "Business" exists
    When Sabine imports the starter package "oea-starter-togaf-classic"
    Then the import is rejected with 409 "package-conflict" listing the layer "architecture layer Business" and the layers are unchanged

  Scenario: Organization layer kept
    Given the layer "Strategy" exists
    And the starter package "oea-starter-togaf-classic" is installed
    When Sabine imports the starter package "oea-starter-togaf-classic"
    Then the layer "Strategy" is unchanged

  Scenario: Layer dropped by a new package version
    Given a package brought the layer "Motivation", assigned to one of its metatypes
    When Sabine imports a version of the package without "Motivation"
    Then "Motivation" no longer exists, and the metatype has no layer
