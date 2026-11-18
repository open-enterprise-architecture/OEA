## ADDED Requirements

### Requirement: Notation of a viewpoint

A viewpoint SHALL have the notation `generic`, `archimate3`, `uml` or `bpmn2`; a viewpoint created without one SHALL get `generic`. An unknown notation SHALL be rejected with 400. Viewpoints created before this requirement SHALL have the notation `generic`.

Trace: US-090, REQ-090, UC-12 step 4, owner decision 2026-11-17

#### Scenario: ArchiMate viewpoint

- **WHEN** Kurt creates the viewpoint "Cloud Security View" with the notation `archimate3`
- **THEN** the response is 201 with the notation `archimate3`

#### Scenario: Existing viewpoint

- **GIVEN** the viewpoint "Integration View" was created without a notation
- **WHEN** Kurt reads it
- **THEN** its notation is `generic`

### Requirement: Notation mapping

A viewpoint SHALL map any of its allowed metatypes to a notation element, with an optional default size (1–2000) and visual hint `#rrggbb`. The element SHALL belong to the notation or be generic, SHALL be an element shape for an entity type and a relationship shape for a connection type; otherwise the request SHALL be rejected with 422 naming `mappings`.

Trace: US-091 AC2, REQ-091, viewpoint BO BR-06, BR-07, owner decision 2026-11-17

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

- **WHEN** Kurt saves widths of 1 and 2000, then of 0 and 2001
- **THEN** the first two are accepted and the others answer 400

#### Scenario: Type not allowed

- **WHEN** Kurt maps "Data Object", which the viewpoint does not allow
- **THEN** the response is 422 naming `mappings`

### Requirement: Fallback rendering

An allowed metatype without a mapping SHALL be rendered as `generic:Box` (a connection type as `generic:Arrow`) with the default size of the catalog; the viewpoint SHALL return the effective element per allowed metatype.

Trace: viewpoint BO NotationMapping, owner decision 2026-11-17

#### Scenario: Unmapped entity type

- **GIVEN** "Cloud Security View" allows "Network Zone" without a mapping
- **WHEN** Kurt reads the viewpoint
- **THEN** "Network Zone" has the effective element `generic:Box`

### Requirement: Element catalog

`listNotationElements` SHALL return the elements of the notations with key, notation, label, kind (element or relationship) and default size; it SHALL need `entity:read` or `viewpoint:manage`. The catalog SHALL contain the elements of ArchiMate 3.2, the component and class elements of UML, the core elements of BPMN 2.0 and the generic elements.

Trace: US-091 AC1, REQ-091, owner decision 2026-11-17

#### Scenario: ArchiMate elements

- **WHEN** Kurt reads the catalog
- **THEN** it contains `archimate:ApplicationComponent` as an element of `archimate3` and `archimate:Flow` as a relationship

### Requirement: System-defined viewpoints are read-only

A viewpoint installed from a metamodel package SHALL be system-defined with its package. Updating or deleting it SHALL be rejected with 422, the problem type `read-only-viewpoint` and the detail "System-defined viewpoints are read-only"; nothing SHALL change.

Trace: US-092 AC1, REQ-092, viewpoint BO BR-02

#### Scenario: Delete a system-defined viewpoint

- **GIVEN** the starter package installed "Application Landscape"
- **WHEN** Kurt deletes it
- **THEN** the response is 422, and the viewpoint stays

### Requirement: Limits of the notation mapping

A write SHALL carry at most 1000 mappings (500 entity and 500 connection types), at most one per metatype, and element keys of at most 100 characters; more mappings, a longer key, a size outside 1–2000 or a hint not of the form `#rrggbb` SHALL be rejected with 400, a second mapping of the same metatype with 422 naming `mappings`. Nothing SHALL change.

Trace: US-091 AC2, REQ-091, security review us-091-viewpoint-notations

#### Scenario: Number of mappings

- **WHEN** Kurt saves a viewpoint that allows 500 entity and 500 connection types with 1000 mappings, then with 1001
- **THEN** the first is accepted, and the second answers 400

#### Scenario: Visual hint

- **WHEN** Kurt saves the visual hint `#1a2B3c`, then `red`
- **THEN** the first is accepted, and the second answers 400

#### Scenario: Same metatype mapped twice

- **WHEN** an API call maps "Application Component" twice in one request
- **THEN** the response is 422 naming `mappings`, and nothing changes

### Requirement: Origin of a viewpoint set only by the import

Only the package import SHALL make a viewpoint system-defined and set its package. A viewpoint created or updated through the API SHALL be user-defined without a package, whatever the request carries; an update or delete of a system-defined viewpoint SHALL be rejected with 422 even with its current version.

Trace: US-092 AC1, REQ-092, viewpoint BO BR-02, security review us-091-viewpoint-notations

#### Scenario: Forged origin

- **WHEN** an API call creates a viewpoint with `viewpointType` `system-defined` and the package of the starter package in the body
- **THEN** the viewpoint is user-defined without a package, and its holder can edit and delete it

#### Scenario: Update with the current version

- **GIVEN** the starter package installed "Application Landscape" with version 1
- **WHEN** Kurt updates it with `If-Match` 1
- **THEN** the response is 422 with the detail "System-defined viewpoints are read-only", and the viewpoint is unchanged

### Requirement: Audit of notation and mappings

`viewpoint.created`, `viewpoint.updated` and `viewpoint.deleted` SHALL name the notation; `viewpoint.updated` SHALL list `notation` and `mappings` among the changed fields when they change. A rejected write, the read-only rejection included, SHALL NOT be audited.

Trace: ADR-033, security review us-091-viewpoint-notations

#### Scenario: Mapping changed

- **WHEN** Kurt changes the mapping of "Application Component" in "Cloud Security View"
- **THEN** `viewpoint.updated` is audited naming `mappings` and the notation `archimate3`

### Requirement: Notation and system viewpoints on SCR-031

The section "Viewpoints" SHALL group system-defined and organization viewpoints and show the notation of each; system-defined viewpoints SHALL show no "Edit" and "Delete". The dialog SHALL offer the notation and, per selected entity type, a choice of the elements of that notation and the generic ones, size and visual hint; changing the notation SHALL clear the elements that no longer fit.

Trace: US-091 AC1, AC3, US-092 AC2, UC-12 steps 2, 4, 8, 9

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
