# Delta for catalog/catalog-query

## ADDED Requirements

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
