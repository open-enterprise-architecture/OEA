# Spec Delta

## MODIFIED Requirements

### Requirement: Default columns

A catalog with configured columns SHALL return its visible columns in their order and with their labels. As long as it has none, its columns SHALL be the entity name and, if the primary metatype maps a property named `Status`, that property. A missing value SHALL be returned as empty.

Trace: US-049 AC1, US-047 AC2–AC4, REQ-046, REQ-044

#### Scenario: Default columns of an application catalog

- **WHEN** a catalog without configured columns for `Application Component` is queried
- **THEN** the columns are "Name" and "Status", in this order, and an application without a status shows an empty status cell

#### Scenario: Configured columns

- **GIVEN** a catalog configured with "Operational Status", "Name" and a hidden "Description"
- **WHEN** it is queried
- **THEN** the columns are "Operational Status" and "Name", in this order

### Requirement: Sorting

The query SHALL sort by one sortable column, ascending or descending; without a sort, by the configured default sort, else by name ascending. Missing values SHALL come last; enum values SHALL follow the order of the enum. Clicking a header SHALL cycle ascending → descending → default order and mark the direction.

Trace: US-049 AC2, US-049 AC3, REQ-046 AC5, US-047, issue #65

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
