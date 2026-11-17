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

A package SHALL carry viewpoints, which the import SHALL install as system-defined viewpoints of the package with the checks of a viewpoint. An unknown type SHALL fail the import with 422; a re-import of the package SHALL update its viewpoints, and a name of an organization viewpoint or another package SHALL be a conflict (409). Shape assignments SHALL stay unsupported (422).

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
