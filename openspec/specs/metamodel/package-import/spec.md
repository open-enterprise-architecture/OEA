# metamodel/package-import Specification

## Purpose

Defines how a metamodel package – metatypes, property categories and property definitions in the portable JSON format – is validated and imported into the instance, how imported content is marked, and how an import is audited.

## Requirements

### Requirement: Validated and limited package input

The system SHALL accept a package only if it is valid JSON, conforms to the package schema of the contract, and stays within the instance limits for size (default 10 MB), nesting depth and element count. A rejected package SHALL change nothing; the response SHALL name the first problem with its location.

Trace: REQ-033, REQ-184 AC1, REQ-184 AC2

#### Scenario: Syntactically faulty package

- **WHEN** a person with `metamodel:write` imports a file that is not valid JSON
- **THEN** the request is rejected with 422, the problem names line and column, and no metatype is stored

#### Scenario: Package violating the schema

- **WHEN** a package contains a metatype without a name or a property definition with an unknown data type
- **THEN** the request is rejected with 422 naming the offending element, and nothing is stored

#### Scenario: Package above the size limit

- **WHEN** a package is larger than the configured maximum
- **THEN** the request is rejected with 413 before it is parsed

#### Scenario: Unsupported package sections

- **WHEN** a package contains viewpoints, shape assignments or translation bundles, or requests the strategy `replace`
- **THEN** the request is rejected with 422 naming the unsupported part, and nothing is stored

### Requirement: Atomic import with scope and origin

A valid package SHALL be imported in one transaction: every new metatype and property definition is stored with `scope=imported` and the package identifier and version as origin; content that the same package imported before is updated; if any element cannot be imported, nothing is stored. Only a person with `metamodel:write` SHALL import packages.

Trace: US-033 AC1, REQ-114, ADR-002, ADR-032

#### Scenario: Package with five metatypes

- **WHEN** a person with `metamodel:write` imports a package with five new metatypes and their property definitions
- **THEN** all five metatypes exist with `scope=imported` and the package as origin, and the result reports five created metatypes

#### Scenario: Re-import of the same package

- **WHEN** the same package is imported again with a changed metatype name
- **THEN** the metatype is updated in place, no duplicate is created, and the result reports one updated metatype

#### Scenario: Import without permission

- **WHEN** a principal without `metamodel:write` imports a package
- **THEN** the request is rejected with 403 and nothing is stored

### Requirement: Content of others is never overwritten

An element of the package whose identifier or name already belongs to content with `scope=organization`, `scope=built-in` or another package SHALL be a conflict: the whole import SHALL be rejected, the conflicts SHALL be listed, and the existing content SHALL remain unchanged.

Trace: REQ-114, ADR-002

#### Scenario: Name already used by another package

- **WHEN** a package contains a metatype whose name is already used by a metatype of another package
- **THEN** the import is rejected with 409, the result lists the conflicting metatype, and no element of the package is stored

### Requirement: Audited import with one batch

Every successful import SHALL write one audit event for the package and one for each created or updated metatype, all with the same batch ID; a rejected import by an authorized principal SHALL be audited with its reason.

Trace: US-033 AC1, REQ-033, REQ-175

#### Scenario: Batch ID of an import

- **WHEN** a package with five metatypes is imported
- **THEN** the audit trail contains `metamodel.package-imported` and five `metamodel.metatype-imported` events with the same batch ID
