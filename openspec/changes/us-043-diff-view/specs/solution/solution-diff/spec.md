# Delta for solution/solution-diff

## Purpose

Defines the diff view of a solution: for each entity the solution changes, its current state in the catalog next to its target state after the solution, computed on each read, and how it is shown on the screen "Solution comparison".

## ADDED Requirements

### Requirement: Read the diff

A principal with `entity:read` SHALL read the diff of a solution through `GET /solutions/{id}/diff`. The diff SHALL be computed on each read and SHALL NOT be stored. A solution without deltas SHALL return an empty list. An unknown solution SHALL answer 404, a caller without `entity:read` 403.

Trace: US-043 AC5, REQ-041 AC4

#### Scenario: No deltas

- **GIVEN** a solution without deltas
- **WHEN** the diff is read
- **THEN** the response is 200 with an empty list

#### Scenario: Unknown solution or missing permission

- **WHEN** the diff of an unknown solution is read
- **THEN** the response is 404
- **AND** a caller without `entity:read` receives 403

### Requirement: Only affected entities

The diff SHALL hold exactly one entry per delta of the solution, in the order of the delta list (`new`, `modified`, `retiring`, each by name, case-insensitive), and no entry for an entity without a delta.

Trace: US-043 AC4, REQ-041 AC5

#### Scenario: Only affected entities

- **GIVEN** the baseline holds 100 entities and the solution holds 3 deltas
- **WHEN** the solution architect reads the diff
- **THEN** it holds exactly 3 entries, one per delta
- **AND** none of the other 97 entities appears

### Requirement: Paging of the diff

The diff SHALL be paged with `page` and `pageSize` from 1 to 200, default 50, and a `cursor`, with the rules of the delta list. It SHALL carry the number of entries per type over all pages and, while a following page exists, an opaque `nextCursor`.

Trace: owner decision 2026-11-13, REQ-071

#### Scenario: Pages by cursor

- **GIVEN** a solution with 120 deltas: 50 `new`, 40 `modified` and 30 `retiring`
- **WHEN** every page of size 50 is loaded with the cursor of the previous one
- **THEN** the pages hold every entry exactly once, in the order of the delta list
- **AND** each page carries the counts 50, 40 and 30, and the last page has no `nextCursor`

#### Scenario: Limits of the page size

- **WHEN** the diff is loaded with page size 1 or 200
- **THEN** the response is 200
- **WHEN** it is loaded with page size 0 or 201
- **THEN** the response is 400

### Requirement: Fields of an entry

Each entry SHALL carry the delta ID and type, the entity ID (none for `new`), the metatype and a list of fields: the name, the description and every property with a value on at least one side, each with its current value, its target value and whether it changes. A reference value SHALL carry the ID and the name of the entity it refers to.

Trace: US-043 AC1–AC3, REQ-041 Processing

#### Scenario: Reference value

- **GIVEN** `ERP-Core` has the reference property "Hosted on" `DC-Zurich`
- **AND** the solution has a delta `modified` for `ERP-Core`
- **WHEN** the diff is read
- **THEN** "Hosted on" carries the ID and the name `DC-Zurich` on both sides

### Requirement: Entry of a retiring entity

For a delta `retiring`, the current state SHALL be the entity in the catalog now; the entry SHALL have no target state, and every field SHALL be marked as changing.

Trace: US-043 AC1, REQ-041 AC1

#### Scenario: Retiring entity

- **GIVEN** the solution has a delta `retiring` for `CRM-Legacy`
- **WHEN** the diff is read
- **THEN** `CRM-Legacy` appears once, with its current state from the catalog and no target state

### Requirement: Entry of a new entity

For a delta `new`, the entry SHALL have no current state; the target state SHALL be the proposed entity with its values, and every field SHALL be marked as changing.

Trace: US-043 AC2, REQ-041 AC1

#### Scenario: New entity

- **GIVEN** the solution has a delta `new` for `Salesforce` (`ApplicationComponent`) with "Owner" `Sales`
- **WHEN** the diff is read
- **THEN** `Salesforce` appears once, with no current state and a target state holding the name and "Owner" `Sales`

### Requirement: Entry of a modified entity

For a delta `modified`, the current state SHALL be the entity in the catalog now, and the target state the catalog with the after values of the delta applied. A field of the delta SHALL change to its after value; every other field SHALL keep the catalog value on both sides and SHALL NOT be marked as changing.

Trace: US-043 AC3, REQ-041 AC2, owner decision 2026-11-13

#### Scenario: Modified entity

- **GIVEN** `ERP-Core` has "Release" `v4` and "Owner" `IT`
- **AND** the solution has a delta `modified` for `ERP-Core` with "Release" `v5`
- **WHEN** the diff is read
- **THEN** `ERP-Core` appears once with "Release" from `v4` to `v5`, marked as changing
- **AND** "Owner" `IT` on both sides, not marked as changing

#### Scenario: Removed value

- **GIVEN** the solution has a delta `modified` for `ERP-Core` that removes "Users" `250`
- **WHEN** the diff is read
- **THEN** "Users" shows `250` as current value and no target value, marked as changing

### Requirement: Drift since capture

For a field of a delta `modified`, the diff SHALL mark the field as changed in the catalog since capture when the before value recorded at capture differs from the current catalog value. The current side SHALL show the catalog value, not the recorded one.

Trace: REQ-041 Processing, owner decision 2026-11-13

#### Scenario: Catalog changed since capture

- **GIVEN** a delta `modified` recorded "Release" from `v4` to `v5`
- **AND** the catalog value of "Release" changed to `v4.1` afterwards
- **WHEN** the diff is read
- **THEN** "Release" shows `v4.1` as current value and `v5` as target value
- **AND** the field is marked as changed in the catalog since capture

#### Scenario: Catalog unchanged

- **GIVEN** a delta `modified` recorded "Release" from `v4` to `v5` and the catalog still holds `v4`
- **WHEN** the diff is read
- **THEN** the field is not marked as changed in the catalog since capture

### Requirement: Entity no longer in the catalog

If the entity of a `modified` or `retiring` delta is no longer in the baseline (deleted after capture), the entry SHALL have no current state and SHALL be marked as no longer in the catalog. For `modified`, the target state SHALL hold the after values of the delta.

Trace: REQ-041 Processing, owner decision 2026-11-13

#### Scenario: Entity deleted after capture

- **GIVEN** the solution has a delta `retiring` for `CRM-Legacy`
- **AND** `CRM-Legacy` was deleted from the catalog afterwards
- **WHEN** the diff is read
- **THEN** the entry for `CRM-Legacy` has no current state and is marked as no longer in the catalog

### Requirement: Visibility in the diff

A property value the reader may not read SHALL be redacted on both sides, with the rule of the delta list: a `role-restricted` property needs a read role, and a `connection-scoped` property needs the entity in the reader's scope. A redacted field SHALL still appear, marked as hidden, and SHALL carry neither value nor a drift mark.

Trace: REQ-041 Processing, ADR-032

#### Scenario: Restricted value redacted

- **GIVEN** `ERP-Core` has a `role-restricted` property "Cost" and the solution has a delta `modified` changing it
- **WHEN** a reader without a read role of "Cost" reads the diff
- **THEN** "Cost" appears as hidden, with no current and no target value

#### Scenario: Restricted value readable

- **WHEN** a reader with a read role of "Cost" reads the same diff
- **THEN** "Cost" shows its current and target values

### Requirement: Screen "Solution comparison"

The screen SCR-062 at `/solutions/{id}/diff` SHALL show one card per entry, with the current state and the target state side by side, stacked on a narrow screen. Every marking SHALL carry a label or an icon, never color alone.

Trace: US-043 AC1–AC3, REQ-041 AC1, AC2, owner decision 2026-11-13

#### Scenario: Two columns per entry

- **GIVEN** a solution with a delta `modified` for `ERP-Core`
- **WHEN** the solution architect opens SCR-062
- **THEN** the card of `ERP-Core` shows the columns "Current state" and "Target state"

### Requirement: Markings on the screen

A `new` entry SHALL be marked "New" with a plus icon, its current column reading "Does not exist yet". A `retiring` entry SHALL be marked "Retiring", its target column showing the entity struck through as "Out of service". In a `modified` entry, changed fields SHALL be highlighted and unchanged fields dimmed.

Trace: US-043 AC1–AC3, REQ-041 AC1, AC2

#### Scenario: Markings of the three types

- **GIVEN** a solution with a delta `new` for `Salesforce`, `modified` for `ERP-Core` with "Release" from `v4` to `v5`, and `retiring` for `CRM-Legacy`
- **WHEN** the solution architect opens SCR-062
- **THEN** `Salesforce` is marked "New" and its current column reads "Does not exist yet"
- **AND** in `ERP-Core` "Release" is highlighted and "Owner" is dimmed
- **AND** `CRM-Legacy` is marked "Retiring" and its target column reads "Out of service"

### Requirement: Drift markings on the screen

A field changed in the catalog since capture SHALL read "Changed in the catalog since capture" with an icon, and an entity no longer in the catalog SHALL read "No longer in the catalog" in its current column.

Trace: REQ-041 Processing, owner decision 2026-11-13

#### Scenario: Drift shown

- **GIVEN** an entry field is marked as changed in the catalog since capture
- **WHEN** SCR-062 shows it
- **THEN** the field carries the text "Changed in the catalog since capture" with an icon

#### Scenario: Entity no longer in the catalog shown

- **GIVEN** an entry is marked as no longer in the catalog
- **WHEN** SCR-062 shows it
- **THEN** its current column reads "No longer in the catalog"

### Requirement: Loading the screen

Without deltas, the screen SHALL show "No changes captured yet" and no error. The screen SHALL load the first page of the diff each time it is opened and offer "Refresh", which loads it again without a full page reload. While a following page exists, it SHALL offer "Load more", which appends the next page. The area "Changes" of SCR-061 SHALL link to it with "Compare".

Trace: US-043 AC5, AC6, REQ-041 AC3, AC4, owner decision 2026-11-13

#### Scenario: Empty diff

- **GIVEN** a solution without deltas
- **WHEN** SCR-062 is opened
- **THEN** it shows "No changes captured yet" and no error

#### Scenario: Updated after saving a delta

- **GIVEN** the solution architect saves another delta on SCR-061
- **WHEN** they open "Compare"
- **THEN** SCR-062 shows the new delta without a full page reload

#### Scenario: Load more

- **GIVEN** a solution with 60 deltas
- **WHEN** SCR-062 is opened
- **THEN** it shows 50 entries, the counts over all 60 and "Load more"
- **WHEN** the solution architect chooses "Load more"
- **THEN** it shows all 60 entries and no longer offers "Load more"

#### Scenario: Refresh

- **GIVEN** SCR-062 is open and another delta was captured meanwhile
- **WHEN** the solution architect chooses "Refresh"
- **THEN** the new entry appears without a full page reload
