# Delta for catalog/catalog-query

## MODIFIED Requirements

### Requirement: Pagination

The query SHALL return one page of rows: `page` counts from 0, `pageSize` defaults to 50 and is at most 200; the response SHALL state the page, the page size, the total number of entries and the number of pages. In rows mode a join result row SHALL count as an entry; only rows passing the filters SHALL count. Sorting SHALL apply before pagination. The table SHALL show the current page and the number of pages and allow moving between pages.

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
