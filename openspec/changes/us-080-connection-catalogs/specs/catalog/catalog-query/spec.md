# Delta for catalog/catalog-query

## ADDED Requirements

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

## MODIFIED Requirements

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
