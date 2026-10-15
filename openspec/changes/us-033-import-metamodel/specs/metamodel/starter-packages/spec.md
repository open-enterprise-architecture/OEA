# Spec Delta

## Purpose

Defines the metamodel packages shipped with OEA as starting points for a new instance (ADR-005): which packages exist, how they are presented and how one is imported without uploading a file.

## ADDED Requirements

### Requirement: Shipped starter packages with summary

The system SHALL list the starter packages shipped with OEA, each with identifier, name, version, description, the number of metatypes and property definitions, the names of its metatypes, and whether it has been imported into the instance. The package `oea-starter-togaf-classic` SHALL be shipped.

Trace: REQ-112 AC1, ADR-005

#### Scenario: Package list of a new instance

- **WHEN** a person with `metamodel:write` lists the starter packages of an instance without metatypes
- **THEN** `oea-starter-togaf-classic` is listed with seven metatypes, among them `Application Component`, `Data Object` and `Technology Component`, and as not imported

### Requirement: Import of a starter package by identifier

A person with `metamodel:write` SHALL import a shipped starter package by its identifier, with the same validation, transaction, conflict and audit rules as any package import.

Trace: REQ-112, US-033 AC1, ADR-005

#### Scenario: Starter configuration imported

- **WHEN** a person with `metamodel:write` imports `oea-starter-togaf-classic`
- **THEN** the seven metatypes exist with `scope=imported`, each with the property `Status` (planned, active, phasing out, retired), the package is listed as imported, and the audit trail contains the import with one batch ID

#### Scenario: Unknown starter package

- **WHEN** a starter package with an unknown identifier is requested
- **THEN** the request is rejected with 404

### Requirement: Starter configuration offered to the metamodel administrator

The start page SHALL offer the shipped starter packages to a person with `metamodel:write` while the instance has no metatype, and SHALL confirm the result of the import.

Trace: ADR-005 (bootstrapping flow), docs/walking-skeleton.md step 5

#### Scenario: Card on a new instance

- **WHEN** a person with `metamodel:write` opens the start page of an instance without metatypes
- **THEN** the card "Import starter configuration" lists `oea-starter-togaf-classic` with its summary, and importing it shows the number of created metatypes

#### Scenario: No card without permission or after the import

- **WHEN** a person without `metamodel:write` opens the start page, or the instance already has metatypes
- **THEN** no starter configuration card is shown
