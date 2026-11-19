# metamodel/viewpoints Specification

## Purpose

Defines the viewpoints of the instance in their generic form: creating, reading, editing and deleting a viewpoint with its allowed metatypes and connection types, the checks, the optimistic locking, the permissions and the audit, and the section "Viewpoints" of the metamodel page (SCR-031).

## Requirements

### Requirement: Create a viewpoint

A person with `viewpoint:manage` SHALL create a viewpoint with a name, an optional description, at least one allowed element metatype and any number of allowed connection metatypes of the instance configuration. The response SHALL be 201 with the viewpoint and its version 1.

Trace: US-090 AC1, REQ-090, UC-12 basic flow, owner decision 2026-11-17

#### Scenario: Viewpoint created

- **GIVEN** Kurt holds `enterprise-architect`
- **WHEN** he creates the viewpoint "Application Landscape" with the metatypes "Application", "Data Object" and "Interface" and the connection type "Data Flow"
- **THEN** the response is 201 with the three metatypes and the connection type, version 1, and the viewpoint is listed

### Requirement: Valid viewpoint

The name SHALL have 1 to 255 characters, SHALL NOT be blank and SHALL be unique case-insensitively; the description SHALL have at most 1000 characters. Allowed metatypes SHALL be element metatypes, allowed connection types connection metatypes, both of the instance configuration. Violations SHALL answer 400 for a length and 422 naming the field otherwise; nothing SHALL be stored.

Trace: US-090, REQ-090, viewpoint BO BR-01, BR-03, BR-04

#### Scenario: Name of 255 characters

- **WHEN** Kurt creates a viewpoint with a name of exactly 255 characters
- **THEN** the response is 201

#### Scenario: Name of 256 characters

- **WHEN** Kurt creates a viewpoint with a name of 256 characters
- **THEN** the response is 400

#### Scenario: Description of 1000 and 1001 characters

- **WHEN** Kurt creates viewpoints with descriptions of 1000 and of 1001 characters
- **THEN** the first is created and the second answers 400

#### Scenario: Name taken

- **GIVEN** the viewpoint "Application Landscape" exists
- **WHEN** Kurt creates a viewpoint named " application landscape "
- **THEN** the response is 422 naming `name`

#### Scenario: No metatype

- **WHEN** Kurt creates a viewpoint without metatypes
- **THEN** the response is 422 naming `metatypeIds`

#### Scenario: Connection metatype as element

- **WHEN** Kurt names the connection type "Data Flow" as an allowed metatype
- **THEN** the response is 422 naming `metatypeIds`

#### Scenario: Element metatype as connection

- **WHEN** Kurt names the metatype "Application" as an allowed connection type
- **THEN** the response is 422 naming `connectionTypeIds`

### Requirement: Read viewpoints

`listViewpoints` SHALL return the viewpoints ordered by name case-insensitively, each with its allowed metatypes and connection types by ID and name. `getViewpoint` SHALL return one or 404. Reading SHALL need `entity:read` or `viewpoint:manage`.

Trace: UC-12 step 2, REQ-090

#### Scenario: Viewpoints listed

- **GIVEN** the viewpoints "Integration View" and "Application Landscape" exist
- **WHEN** a reader with `entity:read` lists the viewpoints
- **THEN** "Application Landscape" comes before "Integration View"

#### Scenario: Unknown viewpoint

- **WHEN** an unknown viewpoint ID is read
- **THEN** the response is 404

### Requirement: Edit a viewpoint

A person with `viewpoint:manage` SHALL replace the name, description and allowed types of a viewpoint with the checks of its creation. The request SHALL carry `If-Match` with the version read: without it the response SHALL be 428, with a stale version 412 with the current version; nothing SHALL change. A successful edit SHALL raise the version by one.

Trace: US-090, UC-12 A1, scr-030 D4

#### Scenario: Metatype added

- **WHEN** Kurt adds the metatype "Business Process" to "Application Landscape" with its current version
- **THEN** the response is 200 with the four metatypes and the version raised by one

#### Scenario: Stale version

- **GIVEN** someone else changed "Application Landscape" after Kurt read it
- **WHEN** Kurt saves with the version he read
- **THEN** the response is 412 with the current version, and nothing changes

#### Scenario: Without If-Match

- **WHEN** Kurt saves without `If-Match`
- **THEN** the response is 428

### Requirement: Delete a viewpoint

A person with `viewpoint:manage` SHALL delete a viewpoint with `If-Match` and its current version; the response SHALL be 204 and the viewpoint SHALL no longer be listed. A stale version SHALL give 412.

Trace: US-090 AC3, US-093 AC2, UC-12 A2

#### Scenario: Viewpoint deleted

- **WHEN** Kurt deletes "Integration View" with its current version
- **THEN** the response is 204, and the viewpoint is no longer listed

### Requirement: Deleted metatype leaves viewpoints

When a metatype is deleted, the viewpoints that allow it SHALL no longer name it.

Trace: viewpoint BO BR-03

#### Scenario: Metatype deleted

- **GIVEN** "Application Landscape" allows the metatype "Legacy System", which has no entities
- **WHEN** the metamodel administrator deletes "Legacy System"
- **THEN** "Application Landscape" no longer names it

### Requirement: Permissions for viewpoints

Creating, editing and deleting SHALL need `viewpoint:manage`; without it the response SHALL be 403 and nothing SHALL change. The metamodel lock mode SHALL NOT apply to viewpoints.

Trace: UC-12 preconditions, ADR-032

#### Scenario: Without viewpoint:manage

- **GIVEN** a person with only `entity:read`
- **WHEN** they create a viewpoint
- **THEN** the response is 403

### Requirement: Audit of viewpoints

Creating, editing and deleting a viewpoint SHALL be audited as `viewpoint.created`, `viewpoint.updated` with the changed fields, and `viewpoint.deleted`, each with the name. A rejected write SHALL NOT be audited.

Trace: ADR-033

#### Scenario: Edit audited

- **WHEN** Kurt changes the description of "Application Landscape"
- **THEN** `viewpoint.updated` is audited naming `description`

### Requirement: Section "Viewpoints"

The metamodel page SHALL have the section "Viewpoints" (SCR-031) listing the viewpoints with their allowed metatypes and connection types. Holders of `viewpoint:manage` SHALL see "New viewpoint", "Edit" and "Delete"; the dialog SHALL offer the element metatypes and the connection metatypes as checkboxes; deleting SHALL ask for confirmation.

Trace: US-090, US-093 AC2, UC-12 steps 1–12

#### Scenario: Reader

- **GIVEN** a reader with `entity:read` only
- **WHEN** they open the section
- **THEN** they see the viewpoints without "New viewpoint", "Edit" and "Delete"

#### Scenario: Delete confirmed

- **WHEN** Kurt chooses "Delete" on "Integration View" and confirms
- **THEN** the viewpoint is removed from the list

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

### Requirement: System-defined viewpoints are read-only

A viewpoint installed from a metamodel package SHALL be system-defined with its package. Updating or deleting it SHALL be rejected with 422, the problem type `read-only-viewpoint` and the detail "System-defined viewpoints are read-only"; nothing SHALL change.

Trace: US-092 AC1, REQ-092, viewpoint BO BR-02

#### Scenario: Delete a system-defined viewpoint

- **GIVEN** the starter package installed "Application Landscape"
- **WHEN** Kurt deletes it
- **THEN** the response is 422, and the viewpoint stays

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
