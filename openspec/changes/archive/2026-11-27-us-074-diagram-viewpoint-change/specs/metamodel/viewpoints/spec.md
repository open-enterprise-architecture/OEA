## MODIFIED Requirements

### Requirement: Section "Viewpoints"

Section "Viewpoints" (SCR-031) SHALL list the viewpoints with their allowed types. Holders of `viewpoint:manage` SHALL see "New viewpoint", "Edit" and "Delete" and the types as checkboxes in the dialog; deleting SHALL be confirmed, warning with the number of diagrams deleted, and removing a type SHALL warn with the number of diagrams that get conflicts.

Trace: US-090 AC2, US-093 AC1, AC2, UC-12, owner decisions 2026-11-19, 2026-11-26

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

- **GIVEN** "Process Model (BPMN 2.0)" is used by 5 diagrams, 3 of which show the metatype "Data Store"
- **WHEN** Kurt edits it and removes "Data Store"
- **THEN** the dialog warns "3 diagrams affected – their shapes of removed types become conflicts", and he can still save

#### Scenario: Type removed that no diagram shows

- **GIVEN** "Process Model (BPMN 2.0)" is used by 5 diagrams, none of which shows "Data Store"
- **WHEN** Kurt removes "Data Store"
- **THEN** the dialog shows no warning about diagrams

## ADDED Requirements

### Requirement: Diagrams affected by a viewpoint edit

`countViewpointAffectedDiagrams` SHALL return, for a proposed set of at most 500 metatypes and 500 connection types, the number of diagrams of the viewpoint that are not deleted and have a shown shape or edge of a type the set does not allow; hidden shapes and edges SHALL NOT count. It SHALL need `viewpoint:manage`; more IDs SHALL answer 400.

Trace: UC-12, owner decision 2026-11-26

#### Scenario: Count of affected diagrams

- **GIVEN** of 5 diagrams of the viewpoint, 2 show "Data Store" and 1 an edge of "Flow"
- **WHEN** Kurt asks for the set without "Data Store" and "Flow"
- **THEN** the answer is 3

#### Scenario: Only hidden shapes

- **GIVEN** the only shape of "Data Store" in a diagram shows a deleted entity
- **WHEN** Kurt asks for the set without "Data Store"
- **THEN** that diagram is not counted

#### Scenario: Without viewpoint:manage

- **WHEN** Anna without `viewpoint:manage` asks for the count
- **THEN** the response is 403

#### Scenario: Limits

- **WHEN** Kurt asks with 500 metatype IDs and 500 connection type IDs, and then with 501 of either
- **THEN** the first is answered with 200, and the others with 400
