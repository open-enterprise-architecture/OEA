## MODIFIED Requirements

### Requirement: Limits and checks of package viewpoints

Each package viewpoint SHALL pass the checks and limits of an API write: a name of 1–255 characters unique within the package, a description of at most 1000, at most 500 metatypes, 500 connection types, 1000 mappings and 1000 sizes, and types of the instance configuration. The section SHALL hold at most 10000 viewpoints. A violation SHALL be rejected with 422 naming `/viewpoints/<i>`.

Trace: REQ-033, REQ-184, security review us-091-viewpoint-notations, owner decisions 2026-11-19

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

#### Scenario: Default sizes in a package

- **WHEN** packages carry a viewpoint with a width of 1 and one of 2000 pt, then one with a width of 0, one of 2001, one with a length for an element metatype and one with two sizes for one metatype
- **THEN** the first two are imported with their sizes, and the others are rejected with 422 naming `/viewpoints/<i>/sizes`, and nothing of them is stored
