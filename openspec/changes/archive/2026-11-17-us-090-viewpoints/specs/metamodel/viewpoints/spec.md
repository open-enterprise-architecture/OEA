## Purpose

Defines the viewpoints of the instance in their generic form: creating, reading, editing and deleting a viewpoint with its allowed metatypes and connection types, the checks, the optimistic locking, the permissions and the audit, and the section "Viewpoints" of the metamodel page (SCR-031).

## ADDED Requirements

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
