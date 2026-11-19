## MODIFIED Requirements

### Requirement: Categories and counts

"Current state" SHALL have the categories Components (element entities not deleted), Connections (connection entities not deleted), Catalogs (the catalogs the reader sees) and Diagrams (diagrams not deleted). A solution SHALL have the category Components (its deltas). Each category SHALL show its count; an empty one SHALL be greyed out, not hidden.

Trace: US-133 AC1, AC2, REQ-139, owner decisions 2026-11-18 and 2026-11-19

#### Scenario: Categories of the current state

- **GIVEN** 4 element entities, 2 connections and no catalog, and one more element entity that is deleted
- **WHEN** Kurt expands "Current state"
- **THEN** it shows "Components (4)", "Connections (2)" and "Catalogs (0)", the last one greyed out

#### Scenario: Components of a solution

- **GIVEN** "CRM Migration" has a delta `new` "CRM Cloud", a delta `modified` for "SAP ERP" and a delta `retiring` for "Legacy CRM"
- **WHEN** Kurt expands "CRM Migration"
- **THEN** it shows exactly "Components (3)"

#### Scenario: Solution without deltas

- **GIVEN** "ERP Upgrade" has no delta
- **WHEN** Kurt expands "ERP Upgrade"
- **THEN** it shows "Components (0)" greyed out

#### Scenario: Category Diagrams

- **GIVEN** the diagrams "Order to Cash" and "Procure to Pay"
- **WHEN** Kurt expands "Current state"
- **THEN** it shows "Diagrams (2)"

#### Scenario: No diagrams

- **WHEN** Kurt expands "Current state" and there is no diagram
- **THEN** it shows "Diagrams (0)" greyed out

## ADDED Requirements

### Requirement: Diagrams in the browser

The category Diagrams SHALL list the diagrams by name, case-insensitively, then by ID, in pages of 1 to 200 (default 50) with "Load more", each with the notation of its viewpoint as marker. A diagram SHALL open its page. A solution SHALL NOT have the category Diagrams: reading it SHALL give 422 naming `category`.

Trace: US-133, REQ-139, owner decision 2026-11-19

#### Scenario: Diagrams listed

- **GIVEN** the diagrams "Procure to Pay" with a `bpmn2` viewpoint and "Order to Cash" with an `archimate3` viewpoint
- **WHEN** Kurt expands "Diagrams (2)"
- **THEN** it lists "Order to Cash" marked `archimate3`, then "Procure to Pay" marked `bpmn2`

#### Scenario: Open a diagram

- **WHEN** Kurt chooses "Order to Cash" under "Diagrams"
- **THEN** the page of the diagram opens

#### Scenario: Second page

- **GIVEN** 51 diagrams
- **WHEN** Kurt expands "Diagrams (51)" and chooses "Load more"
- **THEN** the first page lists 50 diagrams and the second page the 51st

#### Scenario: Category of a solution

- **WHEN** Kurt reads the category `diagrams` of the solution "CRM Migration"
- **THEN** the answer is 422 naming `category`
