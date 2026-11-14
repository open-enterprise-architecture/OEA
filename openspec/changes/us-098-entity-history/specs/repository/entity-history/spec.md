## ADDED Requirements

### Requirement: Version history of an entity

A principal with `entity:read` SHALL read the version history of an entity, newest first.

- **Version:** each version SHALL have its number, the time of the change in UTC, the ID and name of the person who made it, and the fields it changed.
- **Changed field:** a changed field SHALL have its key, its label, whether it is a system field (name, description, logical flag, source or target of a connection), and whether it is restricted. It SHALL carry no value.

Trace: US-098 AC1, AC3, REQ-098

#### Scenario: Timeline with versions

- **WHEN** an enterprise architect opens the history of an entity with 5 versions
- **THEN** the response lists 5 versions, version 5 first and version 1 last

#### Scenario: Changed fields of a version

- **WHEN** version 3 of an entity changed the name and the property `lifecycleStatus`
- **THEN** version 3 lists the system field `name` and the property `lifecycleStatus` with its label, both not restricted and without values

### Requirement: Creation and errors of the history read

Version 1 is the creation and SHALL have no changed fields. The response SHALL name the time and the person of the creation, independent of filters and paging. An unknown entity SHALL be answered with 404. A principal without `entity:read` SHALL be rejected with 403.

Trace: US-098 AC3, REQ-098

#### Scenario: Entity without changes

- **WHEN** the history of an entity with only version 1 is read
- **THEN** the response lists version 1 without changed fields and names its creation time and person

#### Scenario: Unknown entity

- **WHEN** the history of an entity ID that does not exist is read
- **THEN** the request is answered with 404

#### Scenario: Without permission

- **WHEN** a person without `entity:read` reads the history of an entity
- **THEN** the request is rejected with 403

### Requirement: Filters of the history

The history SHALL be filterable by period and by person.

- **Period:** `from` and `to` SHALL be inclusive timestamps. A version SHALL be listed when its time lies within them. `from` after `to` SHALL be answered with 400 naming `from`.
- **Person:** `actorId` SHALL list only the versions made by that person. An `actorId` without versions SHALL give an empty list, not an error.

Trace: US-098 AC2, REQ-098

#### Scenario: Filter by actor

- **WHEN** the history of an entity with 3 versions by Michael and 2 by Kurt is filtered by Michael
- **THEN** only the 3 versions by Michael are listed

#### Scenario: Period boundaries

- **WHEN** the history is filtered with `from` equal to the time of version 2 and `to` equal to the time of version 4
- **THEN** versions 2, 3 and 4 are listed

#### Scenario: Just outside the period

- **WHEN** the history is filtered with `from` one millisecond after the time of version 2 and `to` one millisecond before the time of version 4
- **THEN** only version 3 is listed

#### Scenario: Reversed period

- **WHEN** the history is filtered with `from` after `to`
- **THEN** the request is rejected with 400 naming `from`

### Requirement: Actors of the history

The response SHALL name every person who made a version of the entity, independent of the filters, for the selection of the person filter.

Trace: US-098 AC2, REQ-098

#### Scenario: Actors with a filter

- **WHEN** the history of an entity with versions by Michael and Kurt is filtered by Michael
- **THEN** the actors of the response are Michael and Kurt

### Requirement: Paging of the history

The history SHALL be paged with the page number, a page size of 1 to 200 (default 50) and the cursor of the previous page, like the delta list of a solution. The page metadata SHALL count the versions that match the filters.

Trace: REQ-098, REQ-071

#### Scenario: Exactly one page

- **WHEN** the history of an entity with 50 versions is read with the default page size
- **THEN** one page with 50 versions is returned and there is no next page

#### Scenario: One version more

- **WHEN** the history of an entity with 51 versions is read with the default page size
- **THEN** the first page holds versions 51 to 2, and the next page, read with the cursor, holds version 1

#### Scenario: Page size limits

- **WHEN** the history is read with a page size of 200, of 201 and of 0
- **THEN** 200 is accepted, and 201 and 0 are rejected with 400

### Requirement: Restricted fields in the history

A changed field SHALL be restricted when the reader may not read it, by the same rules as the catalog:

- a `role-restricted` property without a read role of the reader;
- a `connection-scoped` property of an entity outside the reader's scope.

Holders of an override role read every field, and system fields are always readable. A restricted field SHALL carry no value.

Trace: REQ-098, REQ-160, REQ-127, owner decision 2026-11-14

#### Scenario: Restricted field shown as restricted

- **WHEN** a viewer without a read role of the `role-restricted` property `investmentCost` reads a version that changed `investmentCost`, and no history visibility is configured
- **THEN** the version lists `investmentCost` as restricted

### Requirement: Hiding restricted fields

A restricted field SHALL appear with `restricted` set, or SHALL be left out, according to the history visibility of the reader's roles for the entity's metatype. A version whose changed fields are all left out SHALL still be listed, without changed fields, so that the numbering stays complete.

Trace: REQ-098, owner decision 2026-11-14

#### Scenario: Restricted field hidden

- **WHEN** the history visibility of the role `viewer` for the metatype `Application Component` is "hide", and a person whose only role is `viewer` reads a version of an application component that changed `investmentCost` and the name
- **THEN** the version lists only the name

#### Scenario: Readable for a read role

- **WHEN** an enterprise architect with a read role of `investmentCost` reads the same version
- **THEN** the version lists `investmentCost` as not restricted

#### Scenario: Version with only hidden changes

- **WHEN** a version changed only `investmentCost`, and the reader's roles hide it for the metatype
- **THEN** the version is listed without changed fields

#### Scenario: Connection-scoped outside the scope

- **WHEN** a person linked to an element outside the scope of the connection-scoped property `securityClassification` reads a version of an entity that changed it
- **THEN** the version lists `securityClassification` as restricted

#### Scenario: Override role

- **WHEN** a holder of an override role reads a version that changed a `role-restricted` property without being in its read roles
- **THEN** the field is listed as not restricted

### Requirement: Page "Change history"

The page SCR-026 at `/entities/{id}/history` SHALL show the name of the entity and its versions as a timeline, newest first.

- **Entry:** each entry SHALL show the version number, the time in the local time zone, the person and the labels of the changed fields. A restricted field SHALL carry the badge "Restricted". Version 1 SHALL be shown as "Created".
- **Filters:** a selection of the period and of the person SHALL filter the timeline.

Trace: US-098 AC1, AC2, REQ-098

#### Scenario: Filter by person on the page

- **WHEN** the reader chooses "Michael" as person
- **THEN** the timeline shows only Michael's versions

#### Scenario: Restricted badge

- **WHEN** a version lists `investmentCost` as restricted
- **THEN** its entry shows the label of `investmentCost` with the badge "Restricted" and no value

### Requirement: Paging and empty state of the page

Above one page, "Load more" SHALL append the next page. An entity with only version 1 SHALL show "No changes since creation. Created on {date} by {person}."

Trace: US-098 AC3, REQ-098

#### Scenario: Empty history

- **WHEN** the page of an entity with only version 1, created on 2026-11-02 by Kurt, is opened
- **THEN** it shows "No changes since creation. Created on 2026-11-02 by Kurt."

#### Scenario: Load more

- **WHEN** the history of an entity with 51 versions is opened
- **THEN** the page shows 50 entries and "Load more", which adds version 1

### Requirement: Opening the history from the catalog

Each entity row of the catalog (SCR-020) and of the portal catalog SHALL have the action "History", which opens the page "Change history" of the entity. The page SHALL link back to the catalog it was opened from.

Trace: US-098, owner decision 2026-11-14

#### Scenario: Open from the catalog

- **WHEN** an enterprise architect chooses "History" on the row of `CRM System` in a catalog
- **THEN** the page "Change history" of `CRM System` opens with its versions, newest first

#### Scenario: Back to the catalog

- **WHEN** the reader follows the back link of the page
- **THEN** the catalog it was opened from is shown again
