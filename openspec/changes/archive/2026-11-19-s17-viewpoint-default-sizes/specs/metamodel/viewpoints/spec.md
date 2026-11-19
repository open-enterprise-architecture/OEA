## ADDED Requirements

### Requirement: Default sizes per metatype

A viewpoint SHALL store per allowed metatype an optional default size in pt: width and height of 1–2000 for an element metatype, a length of 1–2000 for a connection metatype. Without a stored size, an element metatype SHALL get 90 × 30 pt and a connection metatype 90 pt. The viewpoint SHALL return the effective size of each allowed metatype.

Trace: UC-10, UC-12, US-091, owner decisions 2026-11-19

#### Scenario: Size of an element metatype

- **WHEN** Kurt stores for "Application Component" in "Cloud Security View" the width 120 pt and the height 40 pt
- **THEN** the viewpoint returns "Application Component" with 120 × 40 pt, also without a notation mapping

#### Scenario: Length of a connection metatype

- **WHEN** Kurt stores for "Flow" the length 150 pt
- **THEN** the viewpoint returns "Flow" with the length 150 pt

#### Scenario: No stored size

- **GIVEN** "Network Zone" is allowed without a stored size, and "Serves" without a stored length
- **WHEN** Kurt reads the viewpoint
- **THEN** "Network Zone" has 90 × 30 pt and "Serves" 90 pt, whatever notation element they are mapped to

#### Scenario: Size boundaries

- **WHEN** Kurt stores widths, heights and lengths of 1 and 2000, then of 0 and 2001
- **THEN** 1 and 2000 are accepted, and 0 and 2001 are rejected with 400, and nothing changes

#### Scenario: Wrong kind of size

- **WHEN** an API call stores a length for "Application Component", or a width for "Flow", or only a width without a height
- **THEN** the request is rejected with 422 naming `sizes`, and nothing changes

#### Scenario: Metatype not allowed or twice

- **WHEN** an API call stores a size for "Data Object", which the viewpoint does not allow, or two sizes for "Application Component"
- **THEN** the request is rejected with 422 naming `sizes`, and nothing changes

#### Scenario: Metatype no longer allowed

- **GIVEN** "Application Component" has a stored size in "Cloud Security View"
- **WHEN** Kurt removes "Application Component" from the allowed metatypes
- **THEN** its stored size is removed with it

## MODIFIED Requirements

### Requirement: Notation mapping

A viewpoint SHALL map any of its allowed metatypes to a notation element, with an optional visual hint `#rrggbb`; sizes are stored apart from the mapping. The element SHALL belong to the notation or be generic, SHALL be an element shape for an entity type and a relationship shape for a connection type; otherwise the request SHALL be rejected with 422 naming `mappings`.

Trace: US-091 AC2, REQ-091, viewpoint BO BR-06, BR-07, owner decisions 2026-11-17 and 2026-11-19

#### Scenario: Element of the notation

- **WHEN** Kurt maps "Application Component" to `archimate:ApplicationComponent` in an `archimate3` viewpoint
- **THEN** the response is 200 with the mapping

#### Scenario: Element of another notation

- **WHEN** an API call saves `bpmn:Task` for "Application Component" in an `archimate3` viewpoint
- **THEN** the response is 422 naming `mappings`, and nothing changes

#### Scenario: Generic box in any notation

- **WHEN** Kurt maps "Network Zone" to `generic:Box` in a `bpmn2` viewpoint
- **THEN** the response is 200

#### Scenario: Size boundaries

- **WHEN** an API call sends a mapping that still carries `defaultWidth` 0 and `defaultHeight` 2001
- **THEN** the mapping is stored without a size and the fields are ignored, since sizes are stored only as default sizes with their own boundaries

#### Scenario: Type not allowed

- **WHEN** Kurt maps "Data Object", which the viewpoint does not allow
- **THEN** the response is 422 naming `mappings`

### Requirement: Fallback rendering

An allowed metatype without a mapping SHALL be rendered as `generic:Box` (a connection type as `generic:Arrow`); the viewpoint SHALL return per allowed metatype the effective element and the effective default size.

Trace: viewpoint BO NotationMapping, owner decisions 2026-11-17 and 2026-11-19

#### Scenario: Unmapped entity type

- **GIVEN** "Cloud Security View" allows "Network Zone" without a mapping and without a stored size
- **WHEN** Kurt reads the viewpoint
- **THEN** "Network Zone" has the effective element `generic:Box` and the size 90 × 30 pt

### Requirement: Element catalog

`listNotationElements` SHALL return the elements of the notations with key, notation, label and kind (element or relationship), without sizes; it SHALL need `entity:read` or `viewpoint:manage`. The catalog SHALL contain the elements of ArchiMate 3.2, the component and class elements of UML, the core elements of BPMN 2.0 and the generic elements.

Trace: US-091 AC1, REQ-091, owner decisions 2026-11-17 and 2026-11-19

#### Scenario: ArchiMate elements

- **WHEN** Kurt reads the catalog
- **THEN** it contains `archimate:ApplicationComponent` as an element of `archimate3` and `archimate:Flow` as a relationship

### Requirement: Limits of the notation mapping

A write SHALL carry at most 1000 mappings and at most 1000 sizes (each 500 entity and 500 connection types), at most one of each per metatype, and element keys of at most 100 characters; more entries, a longer key or a hint not of the form `#rrggbb` SHALL be rejected with 400, a second mapping or size of the same metatype with 422 naming the list. Nothing SHALL change.

Trace: US-091 AC2, REQ-091, security review us-091-viewpoint-notations, owner decision 2026-11-19

#### Scenario: Number of mappings

- **WHEN** Kurt saves a viewpoint that allows 500 entity and 500 connection types with 1000 mappings and 1000 sizes, then with 1001 mappings or 1001 sizes
- **THEN** the first is accepted, and the others answer 400

#### Scenario: Visual hint

- **WHEN** Kurt saves the visual hint `#1a2B3c`, then `red`
- **THEN** the first is accepted, and the second answers 400

#### Scenario: Same metatype mapped twice

- **WHEN** an API call maps "Application Component" twice in one request
- **THEN** the response is 422 naming `mappings`, and nothing changes

### Requirement: Audit of notation and mappings

`viewpoint.created`, `viewpoint.updated` and `viewpoint.deleted` SHALL name the notation; `viewpoint.updated` SHALL list `notation`, `mappings` and `sizes` among the changed fields when they change. A rejected write, the read-only rejection included, SHALL NOT be audited.

Trace: ADR-033, security review us-091-viewpoint-notations, owner decision 2026-11-19

#### Scenario: Mapping changed

- **WHEN** Kurt changes the mapping of "Application Component" in "Cloud Security View"
- **THEN** `viewpoint.updated` is audited naming `mappings` and the notation `archimate3`

#### Scenario: Size changed

- **WHEN** Kurt changes the width of "Application Component" in "Cloud Security View"
- **THEN** `viewpoint.updated` is audited naming `sizes`

### Requirement: Notation and system viewpoints on SCR-031

The section "Viewpoints" SHALL group system-defined and organization viewpoints and show the notation of each; system-defined viewpoints SHALL show no "Edit" and "Delete". The dialog SHALL offer the notation and, per selected type, the notation element, the visual hint and the default size in pt with 90 × 30 or 90 as placeholder; changing the notation SHALL clear the elements that no longer fit.

Trace: US-091 AC1, AC3, US-092 AC2, UC-12 steps 2, 4, 8, 9, owner decisions 2026-11-19

#### Scenario: Filtered choice

- **WHEN** Kurt chooses the notation `archimate3` in the dialog
- **THEN** the element choice offers only `archimate:` elements and the generic ones

#### Scenario: Notation changed

- **GIVEN** "Application Component" is mapped to `archimate:ApplicationComponent`
- **WHEN** Kurt changes the notation to `bpmn2`
- **THEN** the mapping is cleared

#### Scenario: System-defined viewpoint in the list

- **WHEN** Kurt views "Application Landscape" of the starter package
- **THEN** it is in the group "System-defined" with a lock and without "Edit" and "Delete"

#### Scenario: Size fields

- **WHEN** Kurt edits "Cloud Security View" with the entity type "Application Component" and the connection type "Flow"
- **THEN** the dialog offers width and height in pt for "Application Component" and a length in pt for "Flow", empty with the placeholders 90 × 30 and 90, and the size stays when the notation changes
