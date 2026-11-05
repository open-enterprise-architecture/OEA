# Spec Delta

## ADDED Requirements

### Requirement: Join results

Each row SHALL carry the results of every join under its ID, with entity ID and target column values, and the result SHALL list the joins with name, mode and columns. In aggregate mode a row SHALL hold all results; in rows mode one each. A primary entity without results SHALL stay, with none.

Trace: US-049 AC1, US-048 AC1, REQ-046 AC1, AC4

#### Scenario: Aggregated interfaces

- **GIVEN** the join "Interfaces" in mode aggregate, and "CRM System" has the interfaces "REST API" and "SFTP Export"
- **WHEN** a reader opens the catalog
- **THEN** "CRM System" appears in one row whose interface cell lists "REST API" and "SFTP Export"

#### Scenario: Application without interfaces

- **WHEN** "ERP" has no interface
- **THEN** "ERP" appears with an empty interface cell

### Requirement: Join mode at runtime

A query MAY override the mode of each join; the override SHALL apply to that query only and SHALL NOT change the catalog. Joins without override SHALL use their default mode, else the catalog's. An unknown join ID SHALL be rejected with 400. SCR-020 SHALL offer "Compact / Rows" per join and keep the mode in the URL.

Trace: US-050 AC1–AC4, REQ-049 AC1–AC5

#### Scenario: Switch to rows

- **GIVEN** "CRM System" has 2 interfaces and the join "Interfaces" is in mode aggregate
- **WHEN** Franz switches "Interfaces" to "Rows"
- **THEN** the table shows 2 rows for "CRM System" without a page reload, and the catalog configuration is unchanged

#### Scenario: Back to compact

- **WHEN** Franz switches "Interfaces" back to "Compact"
- **THEN** "CRM System" appears again in one row with both interfaces

#### Scenario: Joins switched independently

- **GIVEN** the joins "Interfaces" and "Technologies"
- **WHEN** Franz switches only "Interfaces" to "Rows"
- **THEN** the rows are expanded per interface, and the technologies stay listed in one cell

#### Scenario: Unknown join

- **WHEN** the query overrides the mode of a join ID that the catalog does not have
- **THEN** the response is 400 naming the join mode parameter

## MODIFIED Requirements

### Requirement: Sorting

The query SHALL sort by one sortable column, ascending or descending; without a sort, by the configured default sort, else by name ascending. Missing values SHALL come last; enum values SHALL follow the order of the enum. A join column SHALL be sortable only in rows mode. Clicking a header SHALL cycle ascending → descending → default order and mark the direction.

Trace: US-049 AC2, US-049 AC3, REQ-046 AC5, US-047, issue #65, owner decision 2026-11-05

#### Scenario: Sort ascending

- **WHEN** a reader clicks the column header "Name"
- **THEN** the rows are sorted alphabetically ascending by name and the header shows ↑

#### Scenario: Switch sorting

- **WHEN** the reader clicks the header "Name" again
- **THEN** the rows are sorted descending and the header shows ↓
- **AND** a third click returns to the default order without an arrow

#### Scenario: Unknown sort column

- **WHEN** the query is called with a sort column that the catalog does not return
- **THEN** the response is 400 naming the sort parameter

#### Scenario: Status in enum order

- **GIVEN** the enum values of `Status` are ordered "planned", "active", "retired"
- **WHEN** a reader sorts the catalog by status ascending
- **THEN** planned applications come first, then active, then retired ones, and applications without a status last

#### Scenario: Column not sortable

- **WHEN** the query is called with a sort column configured as not sortable
- **THEN** the response is 400 naming the sort parameter

#### Scenario: Sort by a join column

- **GIVEN** the join "Interfaces" in rows mode
- **WHEN** a reader sorts by the interface protocol
- **THEN** the rows are sorted by protocol; in aggregate mode the same sort is rejected with 400

### Requirement: Pagination

The query SHALL return one page of rows: `page` counts from 0, `pageSize` defaults to 50 and is at most 200; the response SHALL state the page, the page size, the total number of entries and the number of pages. In rows mode a join result row SHALL count as an entry. Sorting SHALL apply before pagination. The table SHALL show the current page and the number of pages and allow moving between pages.

Trace: US-049 AC5, REQ-046 AC5, owner decision 2026-11-05

#### Scenario: First of three pages

- **WHEN** the repository contains 120 applications and a reader opens their catalog
- **THEN** the first page shows 50 entries and the pagination control shows "Page 1 of 3"
- **AND** page 2 can be reached through the pagination control

#### Scenario: Second page sorted by name

- **WHEN** the query of a catalog with 60 applications is called with page 1, page size 20 and ascending sort by name
- **THEN** the total is 60 and the rows are entries 21–40 in alphabetical order

#### Scenario: Rows mode counts rows

- **GIVEN** 3 applications with 2, 1 and no interfaces, and the join "Interfaces" in rows mode
- **WHEN** the catalog is queried
- **THEN** the total is 4 entries
