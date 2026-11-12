# solution/solution-baseline Specification

## Purpose

Defines the baseline of a solution: the current IT landscape the solution architect builds the changes of an initiative on, how it is searched, filtered and paged, and how it is shown on the page "Solution".

## Requirements

### Requirement: Baseline from the current catalog

In project mode, the baseline of a solution SHALL list the entities of element metatypes that are not deleted, each once with its current name, metatype and version. A deleted entity SHALL NOT be listed. Reading the baseline SHALL need `entity:read`; an unknown solution SHALL answer 404.

Trace: US-039 AC1–AC3, REQ-039, owner decision 2026-11-12

#### Scenario: Entities of the landscape

- **GIVEN** the catalog holds the applications `CRM-Legacy` and `ERP-Core` and the data flow "Orders" between them
- **WHEN** a solution architect loads the baseline of a new solution
- **THEN** the baseline lists `CRM-Legacy` and `ERP-Core` and no other entry

#### Scenario: Deleted entity

- **GIVEN** `CRM-Legacy` is deleted
- **WHEN** the baseline is loaded
- **THEN** `CRM-Legacy` is not listed

#### Scenario: Current state

- **GIVEN** `ERP-Core` was renamed to `ERP-Core 2`, raising its version to 2
- **WHEN** the baseline is loaded
- **THEN** it lists `ERP-Core 2` with version 2 and not the old name

#### Scenario: Unknown solution

- **WHEN** the baseline of an unknown solution ID is loaded
- **THEN** the response is 404

#### Scenario: Without entity:read

- **WHEN** a principal without `entity:read` loads the baseline
- **THEN** the response is 403

### Requirement: Search and metatype filter

The baseline SHALL be filterable by a part of the name, compared case-insensitively, of at most 255 characters, and by one metatype. Both filters SHALL combine. A longer search term SHALL answer 400; an unknown metatype SHALL give an empty result.

Trace: US-039 AC5, REQ-039 Inputs

#### Scenario: Filter by metatype

- **GIVEN** a baseline with 20 entities, 12 of them applications
- **WHEN** the baseline is filtered by the metatype `Application`
- **THEN** exactly the 12 applications are listed

#### Scenario: Search by part of the name

- **WHEN** the baseline is searched for `erp`
- **THEN** `ERP-Core` is listed and `CRM-Legacy` is not

#### Scenario: Limit of the search term

- **WHEN** the baseline is searched with a term of 255 characters
- **THEN** the response is 200
- **WHEN** it is searched with a term of 256 characters
- **THEN** the response is 400

### Requirement: Order and paging of the baseline

The baseline SHALL be ordered by name, case-insensitively, then by entity ID. It SHALL be paged with `page` and `pageSize` from 1 to 200, default 50, and SHALL carry the total. A page with a following page SHALL carry an opaque `nextCursor`; a query with `cursor` and `page` SHALL return the entries after the cursor's page. A cursor of another query, a changed cursor or one without `page` SHALL answer 400 naming `cursor`.

Trace: US-039 Technical Notes, REQ-039 Outputs, REQ-071

#### Scenario: Pages by cursor

- **GIVEN** a baseline with 60 entities, some with equal names
- **WHEN** every page of size 20 is loaded with the cursor of the previous one
- **THEN** the pages hold every entity exactly once, in the order of the queries by `page`, with the total 60, and the last page has no `nextCursor`

#### Scenario: Limits of the page size

- **WHEN** the baseline is loaded with page size 1 or 200
- **THEN** the response is 200
- **WHEN** it is loaded with page size 0 or 201
- **THEN** the response is 400

#### Scenario: Cursor of another query

- **WHEN** a cursor of the unfiltered baseline is sent with a metatype filter, or a changed cursor is sent
- **THEN** the response is 400 naming `cursor`

### Requirement: Area "Baseline" on the page "Solution"

The page "Solution" SHALL show the baseline as a list with name and metatype, a search field, a metatype filter and paging. When the landscape holds no entity, the area SHALL show the notice "No entities in the current landscape – you can only create new entities"; when only the filters match nothing, it SHALL show that no entity matches.

Trace: US-039 AC4, US-039 AC5, REQ-039 Outputs

#### Scenario: Baseline on the page

- **WHEN** a reader opens a solution while the catalog holds `CRM-Legacy` and `ERP-Core`
- **THEN** the area "Baseline" lists both with their metatype

#### Scenario: Empty landscape

- **GIVEN** the catalog holds no entity
- **WHEN** a reader opens a solution
- **THEN** the area "Baseline" shows "No entities in the current landscape – you can only create new entities"

#### Scenario: No match

- **WHEN** a reader searches the baseline for a name no entity has
- **THEN** the area shows that no entity matches, not the notice of the empty landscape
