## MODIFIED Requirements

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

- **WHEN** a package contains shape assignments, or requests the strategy `replace`
- **THEN** the request is rejected with 422 naming the unsupported part, and nothing is stored

## ADDED Requirements

### Requirement: Viewpoints in a package

A package SHALL carry viewpoints, which the import SHALL install as system-defined viewpoints of the package with the checks of a viewpoint. An unknown type SHALL fail the import with 422; a re-import of the package SHALL update its viewpoints, and a name of an organization viewpoint or of another package SHALL be a conflict (409). Shape assignments SHALL stay unsupported (422).

Trace: US-092 AC3, REQ-059, REQ-033, viewpoint BO BR-08, owner decision 2026-11-17

#### Scenario: Starter package with viewpoints

- **WHEN** the starter package is imported
- **THEN** "Application Landscape" and "Technology Landscape" are system-defined viewpoints with the notation `archimate3`

#### Scenario: Unknown type

- **WHEN** a package carries a viewpoint that allows a metatype ID found neither in the package nor in the instance
- **THEN** the import is rejected with 422 naming the viewpoint, and nothing is stored

#### Scenario: Not overwritten by another package

- **GIVEN** the starter package installed "Application Landscape"
- **WHEN** another package carries a viewpoint named "Application Landscape"
- **THEN** the import is rejected with 409, and the viewpoint of the starter package stays

#### Scenario: Shape assignments

- **WHEN** a package carries shape assignments
- **THEN** the import is rejected with 422 naming `shapeAssignments`

### Requirement: Limits and checks of package viewpoints

Each package viewpoint SHALL pass the checks and limits of a viewpoint written through the API: a name of 1–255 characters unique within the package, a description of at most 1000, at most 500 metatypes, 500 connection types and 1000 mappings, and types of the instance configuration. The section SHALL hold at most 10000 viewpoints. A violation SHALL be rejected with 422 naming `/viewpoints/<i>`.

Trace: REQ-033, REQ-184, security review us-091-viewpoint-notations

#### Scenario: Name length in a package

- **WHEN** packages carry a viewpoint name of 255 characters and one of 256 characters
- **THEN** the first is imported, and the second is rejected with 422 naming its viewpoint, and nothing of it is stored

#### Scenario: Mappings in a package

- **WHEN** packages carry a viewpoint with 1000 mappings and one with 1001 mappings
- **THEN** the first is imported, and the second is rejected with 422 naming its viewpoint

#### Scenario: Two viewpoints of one name

- **WHEN** a package carries two viewpoints named "Application Landscape" and "application landscape"
- **THEN** the import is rejected with 422 naming the second viewpoint

#### Scenario: Invalid visual hint in a package

- **WHEN** a package maps a metatype with the visual hint `red;background:url(x)`
- **THEN** the import is rejected with 422 naming the viewpoint, and nothing is stored

### Requirement: Package viewpoints never take over foreign viewpoints

An imported viewpoint SHALL be identified by its stable ID within its package, as metatypes are. An ID or a name that belongs to a viewpoint of the organization or another package SHALL be a conflict (409). A re-import SHALL change and delete only viewpoints of the same package, and the audit of the import SHALL count the created, updated and deleted viewpoints and name the deleted ones by ID.

Trace: REQ-114, ADR-002, US-092 AC3, security review us-091-viewpoint-notations

#### Scenario: ID of an organization viewpoint

- **GIVEN** the organization viewpoint "Integration View" exists
- **WHEN** a package carries a viewpoint "Integration Overview" with the ID of "Integration View"
- **THEN** the import is rejected with 409, and "Integration View" is unchanged and stays user-defined

#### Scenario: Viewpoint dropped at a re-import

- **GIVEN** a package installed the viewpoints "Application Landscape" and "Technology Landscape"
- **WHEN** a new version of the package without "Technology Landscape" is imported
- **THEN** "Technology Landscape" is deleted, organization viewpoints stay, and `metamodel.package-imported` names its ID among the deleted viewpoints

#### Scenario: Viewpoint renamed at a re-import

- **GIVEN** a package installed "Application Landscape"
- **WHEN** a new version of the package carries the same viewpoint ID with the name "Application Overview"
- **THEN** the viewpoint keeps its ID and is called "Application Overview"
