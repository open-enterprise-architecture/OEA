# catalog/catalog-query Specification

## Purpose

Defines the live query of a catalog: which entities become rows, which columns they carry, how the rows are sorted and paginated, and what a reader may see.

## Requirements

### Requirement: Rows of the primary metatype

The query of a catalog SHALL return one row per active entity of the catalog's primary metatype, with the values of the catalog's columns and the column definitions (key, label, sortable). A catalog whose primary metatype has no active entities SHALL return an empty result, not an error.

Trace: US-049 AC1, US-049 AC4, REQ-046, REQ-046 AC6

#### Scenario: Catalog with three applications

- **WHEN** a reader opens the catalog "Application Inventory" with primary metatype `Application Component` and the repository contains the applications "CRM System", "ERP" and "Intranet"
- **THEN** the table shows 3 rows, one per application, with the columns "Name" and "Status"

#### Scenario: Empty repository

- **WHEN** a reader queries a catalog whose primary metatype has no entities
- **THEN** the response is 200 with no rows and a total of 0 entries
- **AND** the table shows the hint "No entries found. Entities can be captured as a delta in a solution."

### Requirement: Default columns

A catalog with configured columns SHALL return its visible columns in their order and with their labels. As long as it has none, its columns SHALL be the entity name and, if the primary metatype maps a property named `Status`, that property; a connection catalog SHALL show the source name before and the target name after the entity name. A missing value SHALL be returned as empty.

Trace: US-049 AC1, US-047 AC2–AC4, REQ-046, REQ-044, US-080 AC2, owner decision 2026-11-05

#### Scenario: Default columns of an application catalog

- **WHEN** a catalog without configured columns for `Application Component` is queried
- **THEN** the columns are "Name" and "Status", in this order, and an application without a status shows an empty status cell

#### Scenario: Configured columns

- **GIVEN** a catalog configured with "Operational Status", "Name" and a hidden "Description"
- **WHEN** it is queried
- **THEN** the columns are "Operational Status" and "Name", in this order

#### Scenario: Default columns of a connection catalog

- **WHEN** a catalog without configured columns for `Data Flow` is queried
- **THEN** the columns are "Source", "Name" and "Target", in this order, with the keys `source.name`, `name` and `target.name`

### Requirement: Connections as rows

A catalog whose primary metatype is a connection metatype SHALL return one row per active connection of it. Its columns SHALL show the source and target attributes like other columns and SHALL be sortable; a deleted endpoint SHALL show empty cells. A join SHALL start at the connection itself, as for any primary entity (one level, ADR-004).

Trace: US-080 AC1, US-080 AC3, US-080 AC4, REQ-065 AC1–AC5, owner decisions 2026-11-05

#### Scenario: Data flow catalog

- **GIVEN** the data flows "Orders" from "CRM System" to "ERP" and "Invoices" from "ERP" to "Accounting"
- **WHEN** a reader opens the catalog "Data Flow Overview" with primary metatype `Data Flow`
- **THEN** the table shows 2 rows with the source and target application of each data flow

#### Scenario: Sort by source

- **WHEN** a reader sorts the catalog "Data Flow Overview" by "Source" ascending
- **THEN** "Orders" from "CRM System" comes before "Invoices" from "ERP"

#### Scenario: Data objects of a data flow

- **GIVEN** the data flow "Orders" carries the data objects "Customer master data" and "Order" through `carries-data` connections
- **AND** the catalog "Data Flow Overview" has a join over `carries-data` outbound to `Data Object`
- **WHEN** a reader opens the catalog in mode `aggregate`
- **THEN** the row of "Orders" lists both data objects in the join column

#### Scenario: Element catalogs unchanged

- **WHEN** a reader opens a catalog for `Application Component` with a join over `Data Flow`
- **THEN** the result is the same as before connection catalogs

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

The query SHALL return one page of rows, `page` from 0 and `pageSize` 50 by default and at most 200, with the page, the page size, the total number of entries and the number of pages. In rows mode a join result row SHALL count as an entry; only rows passing the filters SHALL count. Sorting SHALL apply before pagination. The table SHALL show the current page and the number of pages and allow moving between them.

Trace: US-049 AC5, REQ-046 AC5, US-051 AC1, owner decisions 2026-11-05

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

#### Scenario: Filtered total

- **GIVEN** 120 applications, 30 of them active
- **WHEN** the catalog is queried with the filter "Status equals active"
- **THEN** the total is 30 entries on 1 page

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

### Requirement: View state survives navigation

The catalog page SHALL keep the sort and the page in its URL without adding a history entry per change, so that returning to the catalog through back navigation or a reload restores the previous view; invalid values SHALL fall back to the default view.

Trace: US-049 AC3, US-049 AC5, ADR-044

#### Scenario: Back to the catalog

- **WHEN** a reader sorts the catalog descending by name, moves to page 2, opens the catalog overview and navigates back
- **THEN** the catalog shows page 2 sorted descending by name with ↓ in the header

#### Scenario: Reload

- **WHEN** the reader reloads the catalog page sorted by status
- **THEN** the catalog is still sorted by status

#### Scenario: Invalid view state in the URL

- **WHEN** the catalog page is opened with an unknown sort column or an invalid page
- **THEN** it shows the first page in the default order and the URL no longer contains the invalid values

### Requirement: Visibility of the query result

Querying a catalog SHALL require `entity:read`; another person's personal catalog SHALL be answered with 404, like a catalog that does not exist. Soft-deleted entities SHALL NOT be returned. A column on a property the caller cannot read SHALL be returned as not readable and not sortable, with empty values in every row and every join result, and the totals SHALL not depend on it.

Trace: US-049, REQ-043 AC4, ADR-019, ADR-032, US-126 AC4, REQ-160 AC2, #68, owner decision 2026-11-08

#### Scenario: Personal catalog of another person

- **WHEN** a reader queries the personal catalog of another person
- **THEN** the response is 404

#### Scenario: Deleted entity

- **WHEN** an entity of the primary metatype is soft-deleted
- **THEN** the query no longer returns it and the total does not count it

#### Scenario: Catalog column empty without read role

- **GIVEN** `investmentCost` is `role-restricted` with the read role `enterprise-architect`, and "Application Inventory" has a column on it
- **WHEN** Anna, a viewer, queries the catalog
- **THEN** the column is returned with `readable: false` and `sortable: false`, and every row has no value for it

#### Scenario: Column readable with the read role

- **WHEN** Kurt, an enterprise architect, queries the same catalog
- **THEN** the column is readable and sortable and shows the values

#### Scenario: Sort by an unreadable column

- **WHEN** Anna queries the catalog with `sort=investmentCost:desc`
- **THEN** the response is 400 naming `sort`

#### Scenario: Connection-scoped property

- **GIVEN** `securityClassification` is `connection-scoped`
- **WHEN** Kurt queries a catalog with a column on it
- **THEN** the column is not readable and has no values

### Requirement: Next page through a cursor

A page with a following page SHALL carry an opaque `nextCursor`. A query with `cursor` and `page` SHALL return the rows after the cursor's page in the same order, with the same total. A cursor SHALL be valid only for its own query; another, changed or unreadable cursor, or one without `page`, SHALL be rejected with 400 naming `cursor`. The table SHALL use the cursor for the next page.

Trace: REQ-071, REQ-046 AC5, #66, owner decision 2026-11-07

#### Scenario: Next page by cursor

- **GIVEN** a catalog with 60 applications sorted by name with page size 20
- **WHEN** page 1 is queried with the `nextCursor` of page 0 and `page=1`
- **THEN** the rows are entries 21–40 in alphabetical order, as with `page=1` alone, and the total is 60

#### Scenario: Cursor with ties, empty values and rows mode

- **GIVEN** applications with equal names, applications without status sorted by status descending, and the join "Interfaces" in rows mode
- **WHEN** every page is loaded through the cursor of the previous one
- **THEN** the pages together hold every row exactly once, in the order of the queries by `page`

#### Scenario: Last page

- **WHEN** the last page of a catalog is queried
- **THEN** the result has no `nextCursor`

#### Scenario: Cursor of another query

- **WHEN** a cursor of a query sorted by name is sent with a query sorted by status, with other filters or to another catalog
- **THEN** the response is 400 naming `cursor`

#### Scenario: Tampered cursor

- **WHEN** a cursor is changed or is not a cursor of OEA
- **THEN** the response is 400 naming `cursor`, and no database error reaches the caller

#### Scenario: Paging forward in the table

- **WHEN** a reader moves from page 1 to page 2 in the table
- **THEN** the page queries with the cursor of page 1, and the URL holds `page=1` as before

### Requirement: Query time limit

A catalog query SHALL be cancelled when its statements take longer than the configured limit, 10 seconds by default. A cancelled query SHALL be answered with 503 and the problem type `urn:oea:problem:query-timeout`, SHALL change no state and SHALL write the audit event `catalog.query-timeout` with the catalog. The limit SHALL be configurable.

Trace: REQ-186, REQ-071, SQL review 2026-11-06 finding L1, owner decision 2026-11-07

#### Scenario: Query above the limit

- **GIVEN** a catalog query limit of 1 second
- **WHEN** a catalog query takes longer than 1 second in the database
- **THEN** the response is 503 with the problem type `urn:oea:problem:query-timeout`, and an audit event `catalog.query-timeout` names the catalog

#### Scenario: Query within the limit

- **WHEN** a catalog query finishes within the limit
- **THEN** it is answered as before
