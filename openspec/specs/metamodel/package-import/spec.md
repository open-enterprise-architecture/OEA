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

- **WHEN** a package contains shape assignments, or requests the strategy `replace`
- **THEN** the request is rejected with 422 naming the unsupported part, and nothing is stored

### Requirement: Atomic import with scope and origin

A valid package SHALL be imported in one transaction: new metatypes and property definitions get `scope=imported` and the package as origin; a base type SHALL be a metatype of the package or a built-in one; content the same package imported before is updated; if any element cannot be imported, nothing is stored. Only a person with `metamodel:write` SHALL import packages.

Trace: US-033 AC1, REQ-114, ADR-002, ADR-032, owner decision 2026-10-31

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

### Requirement: Organization mappings at a re-import

A re-import SHALL replace the package rows of its mappings and keep or remove the organization entries of each re-imported metatype – also those on package mappings – by its effective re-import setting: own value, else that of its base types, else the global setting (default `keep`). On a shared date the organization entry wins.

Trace: owner decisions 2026-10-30 and 2026-10-31, ADR-002, ADR-049

#### Scenario: Default keeps organization mappings

- **WHEN** the package is imported again after an administrator mapped the organization property `Cost Center` to one of its metatypes, with no re-import setting changed
- **THEN** the mappings of the package are replaced and the mapping of `Cost Center` stays

#### Scenario: Removal inherited from the base type

- **WHEN** the global setting is `keep`, an imported base type has the setting `remove`, and the package is imported again
- **THEN** the organization mappings of the base type and of its imported subtypes with the setting `inherit` are removed, and those of subtypes with `keep` stay

#### Scenario: Override of a package necessity kept

- **WHEN** an administrator made the package property `Status` mandatory for an imported metatype from 2026-07-01 and the package is imported again with the setting `keep`
- **THEN** the package rows of `Status` are replaced and the organization entry of 2026-07-01 stays in effect

### Requirement: Labels of a package

A package MAY bring labels per locale for its own metatypes, categories, property definitions and enum values; the import SHALL store them as package entries, and a re-import SHALL replace only them, keeping organization labels. A key outside the package or an unsupported locale SHALL reject the import with 422.

Trace: REQ-152, US-140 AC1, ADR-025, ADR-049, owner decision 2026-11-04

#### Scenario: Starter package with German labels

- **WHEN** a person with `metamodel:write` imports `oea-starter-togaf-classic` 1.1.0
- **THEN** the German labels of its metatypes, of `Status` and of its values are stored as package entries

#### Scenario: Re-import keeps organization labels

- **WHEN** an administrator set a German label for a package metatype and the package is imported again
- **THEN** the package labels are replaced and the organization label stays in effect

#### Scenario: Label of a foreign element

- **WHEN** a package brings a label for a metatype that is not part of the package
- **THEN** the import is rejected with 422 naming the key, and nothing is stored

### Requirement: Conflicts answered as a problem document

A package import rejected because of conflicts SHALL be answered with 409, `application/problem+json`, the problem type `urn:oea:problem:package-conflict`, and the conflicting elements in the extension member `conflictList`, each with the element and the reason. No element of the package SHALL be stored.

Trace: REQ-114, architecture review 2026-11 API-3, issue #262

#### Scenario: Conflicting metatype

- **WHEN** a package with a metatype that belongs to another package is imported
- **THEN** the response is 409 with `application/problem+json`, the problem type `urn:oea:problem:package-conflict` and the metatype in `conflictList`, and nothing is stored

#### Scenario: Conflicts shown on the screen

- **WHEN** the import on the metamodel screen is answered with this 409
- **THEN** the screen lists the conflicting elements with their reasons

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
