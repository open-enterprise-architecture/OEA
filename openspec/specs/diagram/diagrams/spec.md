# diagram/diagrams Specification

## Purpose

Defines diagrams as objects: creating them for a viewpoint, reading and listing them, renaming, deleting and restoring them, who may do so, their audit, and the page "Diagrams" (SCR-067).

## Requirements

### Requirement: Create a diagram

A person or service account with `entity:write` SHALL create a diagram with a name of 1 to 200 characters, a description of at most 2000 and an existing viewpoint; the new diagram SHALL be empty with version 1. Names SHALL be stored trimmed and be unique ignoring case. The viewpoint SHALL change only through `changeDiagramViewpoint`. A principal without a person SHALL get 403.

Trace: US-074, UC-10 step 1, UC-12, business object Diagram, owner decisions 2026-11-19

#### Scenario: Diagram created

- **WHEN** Anna, holding `entity:write`, creates "Order to Cash" with the viewpoint "Process Model (BPMN 2.0)"
- **THEN** the response is 201 with the empty diagram, its viewpoint, version 1 and Anna as creator

#### Scenario: Service account

- **WHEN** a service account holding `entity:write` creates the diagram "Imported Processes"
- **THEN** the response is 201, and the service account is the creator

#### Scenario: Without entity:write

- **WHEN** Franz, holding only `entity:read`, creates a diagram
- **THEN** the response is 403, and no diagram is created

#### Scenario: Local system admin account

- **WHEN** the local system admin account creates a diagram
- **THEN** the response is 403, and no diagram is created

#### Scenario: Name taken

- **GIVEN** the diagram "Order to Cash"
- **WHEN** Anna creates a diagram named " order to cash "
- **THEN** the response is 422 `diagram-name-taken` naming `name`, and no diagram is created

#### Scenario: Unknown viewpoint

- **WHEN** Anna creates a diagram with a viewpoint ID that does not exist
- **THEN** the response is 422 naming `viewpointId`, and no diagram is created

#### Scenario: Limits

- **WHEN** Anna creates diagrams named with 1, 200 and 201 characters and with only blanks, and with descriptions of 2000 and 2001 characters
- **THEN** the names of 1 and 200 and the description of 2000 are accepted, and the others are rejected with 422 naming the field

#### Scenario: Viewpoint fixed

- **WHEN** Anna changes "Order to Cash" through `updateDiagram` with another viewpoint ID in the request
- **THEN** the diagram keeps its viewpoint; only `changeDiagramViewpoint` changes it

### Requirement: Read and list diagrams

A principal with `entity:read` SHALL read a diagram and list the diagrams ordered by name ignoring case, then by ID, in pages of 1 to 200 (default 50) with a cursor to the next page, optionally filtered by a name part of at most 200 characters matched literally. Each entry SHALL carry name, description, viewpoint with notation, version, creator and the time of the last change. An unknown diagram SHALL answer 404.

Trace: US-074, UC-10, owner decision 2026-11-19

#### Scenario: List

- **GIVEN** the diagrams "Order to Cash", "hire to retire" and "Procure to Pay"
- **WHEN** Franz, holding `entity:read`, lists the diagrams
- **THEN** the list is "hire to retire", "Order to Cash", "Procure to Pay", each with its viewpoint

#### Scenario: Name filter

- **WHEN** Franz lists the diagrams with the filter "to p"
- **THEN** the list is "Procure to Pay"

#### Scenario: Wildcards matched literally

- **GIVEN** the diagrams "100% Digital" and "1000 Days"
- **WHEN** Franz lists the diagrams with the filter "0%"
- **THEN** the list is "100% Digital"

#### Scenario: Next page

- **GIVEN** 51 diagrams
- **WHEN** Franz lists the diagrams and then the next page with the cursor of the first
- **THEN** the first page has 50 diagrams, and the second page the 51st

#### Scenario: Page limits

- **WHEN** Franz lists with page sizes 1, 200, 0 and 201, and with a filter of 200 and of 201 characters
- **THEN** 1, 200 and the filter of 200 are accepted, and 0, 201 and the filter of 201 are rejected with 400

#### Scenario: Unknown diagram

- **WHEN** Franz reads a diagram ID that does not exist
- **THEN** the response is 404

#### Scenario: Without permission

- **WHEN** a principal without `entity:read` lists the diagrams or reads one
- **THEN** the response is 403

### Requirement: Change and delete a diagram

A principal with `entity:write` SHALL rename a diagram and change its description, with the checks of its creation, and SHALL delete it; a deleted diagram SHALL be kept restorable, SHALL NOT be listed or readable, and its name SHALL become free, and the entities of the repository SHALL stay untouched. Every change SHALL need `If-Match` with the version: 428 without, 412 when stale; a change SHALL raise the version.

Trace: US-074, business object Diagram BR-DG-02, owner decision 2026-11-19

#### Scenario: Renamed

- **WHEN** Anna renames "Order to Cash" to "Order to Cash (2026)" with its current version
- **THEN** the response is 200 with the new name and the next version

#### Scenario: Stale version

- **GIVEN** the diagram has version 3
- **WHEN** Anna renames it with `If-Match: 2`, and then without `If-Match`
- **THEN** the first request gives 412, the second 428, and nothing changes

#### Scenario: Rename to a taken name

- **GIVEN** the diagrams "Order to Cash" and "Procure to Pay"
- **WHEN** Anna renames "Procure to Pay" to "ORDER TO CASH"
- **THEN** the response is 422 `diagram-name-taken` naming `name`, and nothing changes

#### Scenario: Deleted

- **WHEN** Anna deletes "Order to Cash" with its current version
- **THEN** the response is 204, the diagram is no longer listed, its ID answers 404, it can be restored, and the entities of the repository are unchanged

#### Scenario: Without entity:write

- **WHEN** Franz, holding only `entity:read`, renames or deletes a diagram
- **THEN** the response is 403, and nothing changes

### Requirement: Restore a diagram

A principal with `entity:restore` SHALL list the deleted diagrams with time and actor of the deletion, and SHALL restore one with `If-Match` and its version; it SHALL then be listed again and its navigation tree entries SHALL show again. Without `entity:restore` both SHALL give 403.

Trace: business object Diagram (lifecycle), ADR-032 (`entity:restore`), owner decision 2026-11-19

#### Scenario: Restored

- **GIVEN** Anna deleted "Order to Cash", which was placed in "Processes"
- **WHEN** Kurt, holding `entity:restore`, lists the deleted diagrams and restores "Order to Cash" with its version
- **THEN** the response is 200, the diagram is listed again with the next version, and "Processes" shows its entry again

#### Scenario: Name taken in the meantime

- **GIVEN** "Order to Cash" was deleted and a new diagram "Order to Cash" was created
- **WHEN** Kurt restores the deleted one
- **THEN** the response is 422 `diagram-name-taken` naming `name`, and the diagram stays deleted

#### Scenario: Not deleted

- **WHEN** Kurt restores a diagram that is not deleted
- **THEN** the response is 409, and nothing changes

#### Scenario: Stale version

- **WHEN** Kurt restores a deleted diagram with a stale `If-Match`, and then without `If-Match`
- **THEN** the first request gives 412, the second 428, and the diagram stays deleted

#### Scenario: Without entity:restore

- **WHEN** Anna, holding `entity:write` but not `entity:restore`, lists the deleted diagrams or restores one
- **THEN** both requests are rejected with 403

#### Scenario: Restore on the page

- **WHEN** Kurt switches on "Show deleted" on the page "Diagrams" and chooses "Restore" on "Order to Cash"
- **THEN** the diagram is restored and listed among the diagrams again

### Requirement: Audit of diagrams

Creating, changing, deleting and restoring a diagram SHALL be audited as `diagram.created`, `diagram.updated` (with the changed fields), `diagram.deleted` and `diagram.restored`, each with the actor, the diagram ID, its name and its viewpoint ID. A diagram deleted with its viewpoint SHALL name the cause `viewpoint-deleted`. A rejected write SHALL NOT be audited.

Trace: ADR-033, business object Diagram, owner decision 2026-11-19

#### Scenario: Rename audited

- **WHEN** Anna renames a diagram
- **THEN** `diagram.updated` is audited with Anna, the diagram ID and the changed field `name`

#### Scenario: Rejected write

- **WHEN** Anna renames a diagram with a stale version
- **THEN** no event is audited

### Requirement: Diagrams page

The web app SHALL offer the page "Diagrams" (SCR-067) with the diagrams, their viewpoint and notation, a name filter, "Load more", and for holders of `entity:write` "Create diagram", "Rename" and "Delete" with a confirmation, and for holders of `entity:restore` "Show deleted" with "Restore". A diagram SHALL open its page with the canvas (SCR-021).

Trace: US-074, UC-10 step 1, owner decision 2026-11-19

#### Scenario: Create from the page

- **WHEN** Anna chooses "Create diagram", enters "Order to Cash" and the viewpoint "Process Model (BPMN 2.0)"
- **THEN** the diagram is created and its page opens

#### Scenario: Reader

- **WHEN** Franz, holding only `entity:read`, opens the page "Diagrams"
- **THEN** he sees the diagrams without "Create diagram", "Rename" and "Delete"

#### Scenario: No diagrams

- **WHEN** Anna opens the page "Diagrams" and there is none
- **THEN** it shows "No diagrams yet." and "Create diagram"
