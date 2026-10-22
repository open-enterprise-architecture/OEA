# Spec Delta

## Purpose

Defines the live query of a catalog: which entities become rows, which columns they carry, how the rows are sorted and paginated, and what a reader may see.

## ADDED Requirements

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

As long as a catalog has no configured columns, its columns SHALL be the entity name and, if the primary metatype maps a property named `Status`, that property; a missing value SHALL be returned as empty.

Trace: US-049 AC1, REQ-046

#### Scenario: Default columns of an application catalog

- **WHEN** a catalog without configured columns for `Application Component` is queried
- **THEN** the columns are "Name" and "Status", in this order, and an application without a status shows an empty status cell

### Requirement: Sorting

The query SHALL sort the rows by one returned column in ascending or descending order; without a sort, the rows SHALL be ordered by name ascending. Rows without a value in the sort column SHALL come last. In the table, clicking a column header SHALL cycle ascending → descending → default order and mark the active direction with an arrow.

Trace: US-049 AC2, US-049 AC3, REQ-046 AC5

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

### Requirement: Pagination

The query SHALL return one page of rows: `page` counts from 0, `pageSize` defaults to 50 and is at most 200; the response SHALL state the page, the page size, the total number of entries and the number of pages. Sorting SHALL apply before pagination. The table SHALL show the current page and the number of pages and allow moving between pages.

Trace: US-049 AC5, REQ-046 AC5

#### Scenario: First of three pages

- **WHEN** the repository contains 120 applications and a reader opens their catalog
- **THEN** the first page shows 50 entries and the pagination control shows "Page 1 of 3"
- **AND** page 2 can be reached through the pagination control

#### Scenario: Second page sorted by name

- **WHEN** the query of a catalog with 60 applications is called with page 1, page size 20 and ascending sort by name
- **THEN** the total is 60 and the rows are entries 21–40 in alphabetical order

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

Querying a catalog SHALL require `entity:read`; another person's personal catalog SHALL be answered with 404, like a catalog that does not exist. Soft-deleted entities SHALL NOT be returned, and only properties with visibility `public` SHALL appear as columns.

Trace: US-049, REQ-043 AC4, ADR-019, ADR-032

#### Scenario: Personal catalog of another person

- **WHEN** a reader queries the personal catalog of another person
- **THEN** the response is 404

#### Scenario: Deleted entity

- **WHEN** an entity of the primary metatype is soft-deleted
- **THEN** the query no longer returns it and the total does not count it
