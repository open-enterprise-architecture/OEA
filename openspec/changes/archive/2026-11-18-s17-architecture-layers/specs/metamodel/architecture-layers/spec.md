## ADDED Requirements

### Requirement: Architecture layers

Holders of `metamodel:write` SHALL create, rename, reorder and delete the architecture layers of the instance metamodel: a name of 1 to 100 characters, unique regardless of case (409 `name-taken`), at most 20 layers. Readers with `entity:read` or `metamodel:write` SHALL list them in their order. In `import-only` writes SHALL give 403 `metamodel-locked`. Each change SHALL be audited.

Trace: US-057 AC4, REQ-053 BR-04, US-035, ADR-017, owner decision 2026-11-18

#### Scenario: Create a layer

- **WHEN** Sabine creates the layer "Business"
- **THEN** it is listed after the existing layers, and the audit records `metamodel.layer-created`, listed in the metamodel changes under the kind `layer`

#### Scenario: Name at the limits

- **WHEN** Sabine creates one layer with a name of 100 characters, one with 101 characters and one with only blanks
- **THEN** the layer with 100 characters is created, and the others are rejected with 422 naming `name`

#### Scenario: Name taken

- **GIVEN** the layer "Business" exists
- **WHEN** Sabine creates the layer "business"
- **THEN** the request is rejected with 409 `name-taken`, and no layer is created

#### Scenario: Number of layers at the limit

- **GIVEN** 19 layers exist
- **WHEN** Sabine creates a layer, and then another one
- **THEN** the 20th is created, and the 21st is rejected with 422

#### Scenario: Reorder

- **WHEN** Sabine moves "Data" before "Application"
- **THEN** the list shows "Data" before "Application" for every reader

#### Scenario: Reader without metamodel:write

- **WHEN** Franz, holding only `viewer`, creates a layer
- **THEN** the request is rejected with 403

#### Scenario: Locked metamodel

- **GIVEN** the instance configuration is `import-only`
- **WHEN** Sabine renames an organization layer
- **THEN** the request is rejected with 403 `metamodel-locked`, and the layer is unchanged

### Requirement: Labels of a layer

A layer SHALL be an element of the metamodel labels with the key `architecture-layer.{id}.name`: administrators SHALL set its organization labels per locale, packages SHALL bring package labels, and the label bundle SHALL carry it like a metatype.

Trace: REQ-152, US-140, ADR-025, ADR-049

#### Scenario: German label of an organization layer

- **WHEN** Sabine sets the German label "Strategie" for the organization layer "Strategy"
- **THEN** the German label bundle carries "Strategie" under the key of the layer

### Requirement: Layer of a metatype

A metatype of any configuration SHALL have at most one layer of the instance metamodel, or none. An unknown layer SHALL give 422 naming `architectureLayerId`. A layer still assigned SHALL NOT be deleted (409 `in-use` with the number of metatypes); an imported layer SHALL be read-only (409).

Trace: US-057 AC4, REQ-053 BR-04, ADR-017

#### Scenario: Assign a layer

- **WHEN** Sabine sets the layer "Application" on the metatype "Business Application"
- **THEN** the metatype returns that layer, and the change is audited like other metatype changes

#### Scenario: Unknown layer

- **WHEN** Sabine sets a layer ID that names no layer on a metatype
- **THEN** the request is rejected with 422 naming `architectureLayerId`, and the metatype is unchanged

#### Scenario: Delete a layer in use

- **GIVEN** "Application" is the layer of two metatypes
- **WHEN** Sabine deletes "Application"
- **THEN** the request is rejected with 409 `in-use` with the count 2, and the layer stays

#### Scenario: Imported layer

- **WHEN** Sabine renames the layer "Technology" of the starter package
- **THEN** the request is rejected with 409, and the layer is unchanged

### Requirement: Layers in packages

A package SHALL bring layers in `architectureLayers`, referenced by its metatypes (422 outside the package); an ID or name of another origin SHALL be a conflict (409). A re-import SHALL replace its own layers, clear assignments to dropped ones and keep organization layers. The starter package 1.4.0 SHALL bring Business, Data, Application and Technology.

Trace: US-057 AC4, REQ-114, ADR-002, ADR-049, owner decision 2026-11-18

#### Scenario: Starter package with layers

- **WHEN** the starter package 1.4.0 is imported
- **THEN** the layers Business, Data, Application and Technology exist as imported, "Application Component" has the layer Application and "Data Object" the layer Data

#### Scenario: Reference to an unknown layer

- **WHEN** a package's metatype references a layer ID that is not in the package
- **THEN** the import is rejected with 422, and nothing is imported

#### Scenario: Name of an organization layer

- **GIVEN** the organization layer "Business" exists
- **WHEN** the starter package 1.4.0 is imported
- **THEN** the import is rejected with 409 `package-conflict` listing the layer, and nothing is imported

#### Scenario: Organization layer kept

- **GIVEN** the organization layer "Strategy" exists
- **WHEN** the starter package is imported again
- **THEN** "Strategy" is unchanged

#### Scenario: Layer dropped by a new package version

- **GIVEN** a package brought the layer "Motivation", assigned to one of its metatypes
- **WHEN** a version of the package without "Motivation" is imported
- **THEN** "Motivation" no longer exists, and the metatype has no layer
