## MODIFIED Requirements

### Requirement: Delete a viewpoint

A person with `viewpoint:manage` SHALL delete a viewpoint with `If-Match` and its current version; the response SHALL be 204, the viewpoint SHALL no longer be listed, and its diagrams, the deleted ones included, SHALL be removed for good in the same transaction, each audited, while the entities stay. A stale version SHALL give 412, and nothing SHALL be deleted.

Trace: US-090 AC3, US-093 AC1, AC2, UC-12 A2, owner decision 2026-11-19

#### Scenario: Viewpoint deleted

- **WHEN** Kurt deletes "Integration View" with its current version
- **THEN** the response is 204, and the viewpoint is no longer listed

#### Scenario: Viewpoint with diagrams

- **GIVEN** "Process Model (BPMN 2.0)" is used by 2 diagrams
- **WHEN** Kurt deletes it with its current version
- **THEN** the viewpoint and both diagrams are gone, each diagram audited as `diagram.deleted` with the cause `viewpoint-deleted`, and the entities of the repository are unchanged

#### Scenario: Stale version keeps the diagrams

- **GIVEN** "Process Model (BPMN 2.0)" is used by 2 diagrams
- **WHEN** Kurt deletes it with a stale version
- **THEN** the response is 412, and the viewpoint and both diagrams stay

### Requirement: Section "Viewpoints"

The metamodel page SHALL have the section "Viewpoints" (SCR-031) listing the viewpoints with their allowed metatypes and connection types. Holders of `viewpoint:manage` SHALL see "New viewpoint", "Edit" and "Delete"; the dialog SHALL offer the element and connection metatypes as checkboxes; deleting SHALL ask for confirmation, warning with the number of diagrams that will be deleted.

Trace: US-090, US-090 AC2, US-093 AC1, AC2, UC-12 steps 1–12, owner decision 2026-11-19

#### Scenario: Reader

- **GIVEN** a reader with `entity:read` only
- **WHEN** they open the section
- **THEN** they see the viewpoints without "New viewpoint", "Edit" and "Delete"

#### Scenario: Delete confirmed

- **WHEN** Kurt chooses "Delete" on "Integration View" and confirms
- **THEN** the viewpoint is removed from the list

#### Scenario: Viewpoint with diagrams

- **GIVEN** "Process Model (BPMN 2.0)" is used by 5 diagrams
- **WHEN** Kurt chooses "Delete" on it
- **THEN** the confirmation says "5 diagrams will be deleted"

#### Scenario: Unused viewpoint

- **WHEN** Kurt chooses "Delete" on a viewpoint without diagrams
- **THEN** the confirmation has no warning about diagrams

#### Scenario: Type removed from a used viewpoint

- **GIVEN** "Process Model (BPMN 2.0)" is used by 5 diagrams
- **WHEN** Kurt edits it and removes an allowed metatype
- **THEN** the dialog warns "5 diagrams affected", and he can still save

## ADDED Requirements

### Requirement: Diagram count of a viewpoint

Reading, listing, creating and updating viewpoints SHALL return per viewpoint the number of diagrams that use it.

Trace: US-090 AC2, US-093 AC1, owner decision 2026-11-19

#### Scenario: Count

- **GIVEN** "Process Model (BPMN 2.0)" is used by 5 diagrams and "Integration View" by none
- **WHEN** Kurt lists the viewpoints
- **THEN** "Process Model (BPMN 2.0)" has the diagram count 5 and "Integration View" 0
