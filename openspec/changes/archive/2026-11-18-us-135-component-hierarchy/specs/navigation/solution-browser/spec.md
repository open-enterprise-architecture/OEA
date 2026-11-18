## MODIFIED Requirements

### Requirement: Contents of a category

A category SHALL list its objects by name, case-insensitively, then by ID, in pages of 1 to 200 (default 50) with "Load more". "Components" of "Current state" SHALL list the root level; elements with children SHALL expand. Items SHALL show their metatype abbreviation, a folder icon (structuring aids) or their delta type (solutions). An entity SHALL open its history, a catalog its page, a delta its solution.

Trace: US-133 AC1, AC2, US-135 AC1, US-136 AC1, REQ-139, REQ-141

#### Scenario: Delta types in a solution

- **GIVEN** the deltas of "CRM Migration" from above
- **WHEN** Kurt expands "Components (3)"
- **THEN** it lists "CRM Cloud" marked new, "Legacy CRM" marked retiring and "SAP ERP" marked modified, in this order

#### Scenario: Second page

- **GIVEN** 51 element entities without a parent
- **WHEN** Kurt expands "Components (51)" of "Current state" and chooses "Load more"
- **THEN** the first page lists 50 entities and the second page the 51st

#### Scenario: Open an entity

- **WHEN** Kurt chooses "SAP ERP" under "Current state"
- **THEN** the change history of "SAP ERP" opens

#### Scenario: Nested components

- **GIVEN** "Finance module" has the parent "SAP S/4HANA", and "Finance landscape" is a structuring aid holding "Payroll"
- **WHEN** Kurt expands "Components" of "Current state"
- **THEN** the root level lists "Finance landscape" with a folder icon and "SAP S/4HANA", and expanding them shows "Payroll" and "Finance module"

#### Scenario: Child of a deleted parent

- **GIVEN** "Finance module" has the parent "SAP S/4HANA", and "SAP S/4HANA" was deleted
- **WHEN** Kurt expands "Components" of "Current state"
- **THEN** "Finance module" is listed at the root level

## ADDED Requirements

### Requirement: Arrange components in the browser

Holders of `entity:write` SHALL set the parent of a component of "Current state" by dragging it onto another component or onto "Components" (root level), or with "Move to…" for keyboard and touch. They SHALL create a structuring aid with "New structuring aid"; holders of `entity:delete` too SHALL delete one after a warning naming its number of children.

Trace: US-135 AC1–AC3, US-136 AC1–AC3, REQ-141

#### Scenario: Drag a component onto another one

- **WHEN** Michael drags "Finance module" onto "SAP S/4HANA"
- **THEN** "Finance module" is shown under "SAP S/4HANA", and "Connections" counts one more connection when the composition was created

#### Scenario: Invalid drop target

- **GIVEN** "Finance module" has the parent "SAP S/4HANA"
- **WHEN** Michael drags "SAP S/4HANA" onto "Finance module"
- **THEN** the drop is refused and nothing is sent

#### Scenario: Delete a structuring aid

- **GIVEN** the structuring aid "Finance landscape" with 3 children
- **WHEN** Kurt chooses "Delete" on it
- **THEN** a warning says that its 3 children move to the root level and are not deleted, and after confirming they are at the root level

#### Scenario: Reader without entity:write

- **WHEN** Franz, holding only `viewer`, opens the browser
- **THEN** he can neither drag components nor sees "Move to…", "New structuring aid" or "Delete"
