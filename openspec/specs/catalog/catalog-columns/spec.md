# catalog/catalog-columns Specification

## Purpose

Defines the column configuration of a catalog: which attributes of the primary metatype become columns, in which order, with which label, whether they are visible and sortable, the default sort, and who may configure them.

## Requirements

### Requirement: All attributes as columns

The configuration SHALL offer every system field and every non-retired property mapped to the primary metatype, inherited and restricted ones included. A connection catalog SHALL also offer them for its source and target: the properties of the side's allowed endpoint metatypes and their subtypes, or all properties without a constraint. An unknown, unoffered or duplicate attribute SHALL be rejected with 422.

Trace: US-047 AC1, REQ-044 AC2, US-080 AC2, REQ-065 AC5, owner decisions 2026-11-05, #68, owner decision 2026-11-08

#### Scenario: Built-in and custom attributes

- **GIVEN** a catalog for `Application Component`, which maps the properties `Vendor` and `Support End Date`
- **WHEN** Kurt opens the column configuration
- **THEN** name, description and the other system fields as well as `Vendor` and `Support End Date` are offered as columns

#### Scenario: Restricted property offered

- **GIVEN** `Application Component` maps `investmentCost`, which is `role-restricted`
- **WHEN** Kurt configures a column on `investmentCost`
- **THEN** the column is saved

#### Scenario: Attribute of another metatype

- **WHEN** the columns of the catalog name a property that `Application Component` does not map
- **THEN** the change is rejected with 422 naming the column

#### Scenario: Attributes of source and target

- **GIVEN** a catalog for `Data Flow`, whose allowed source and target is `Application Component` with the property `Vendor`
- **WHEN** Lukas opens the column configuration
- **THEN** besides the attributes of `Data Flow`, the groups "Source" and "Target" offer the system fields and `Vendor`
- **AND** a column "Source: Vendor" shows the vendor of each data flow's source application

#### Scenario: Endpoint column outside a connection catalog

- **WHEN** the columns of a catalog for `Application Component`, or the target columns of a join, name an attribute of a source or target
- **THEN** the change is rejected with 422 naming the column

### Requirement: Order, label and visibility

The columns SHALL be stored in the order given. A column SHALL have an optional label replacing the attribute name in the header, and a hidden column SHALL stay in the configuration. At least one column SHALL be visible, otherwise the change SHALL be rejected with 422.

Trace: US-047 AC2–AC5, REQ-044 AC1, AC3, AC5

#### Scenario: Status before name

- **WHEN** Kurt moves the column "Status" before "Name" and saves
- **THEN** the catalog shows "Status" as first column, also after reloading

#### Scenario: Own label

- **WHEN** Kurt sets the label "Operational Status" for the status column
- **THEN** the column header shows "Operational Status" and the column still refers to the property `Status`

#### Scenario: Hidden column

- **WHEN** Kurt hides the column "Description"
- **THEN** the table does not show it, and the configuration still lists it to be shown again

#### Scenario: No visible column

- **WHEN** Kurt hides all columns
- **THEN** the dialog shows "At least one column must be visible" and saving is not possible, and the API rejects such a list with 422

### Requirement: Sortable columns and default sort

A column SHALL be sortable unless configured otherwise; at most one column SHALL carry a default sort direction, used when no sort is requested.

Trace: REQ-044, REQ-046 AC5, owner decision 2026-11-05

#### Scenario: Default sort by status

- **GIVEN** the status column has the default sort direction descending
- **WHEN** a reader opens the catalog without choosing a sort
- **THEN** the rows are sorted by status descending

#### Scenario: Two default sorts

- **WHEN** two columns carry a default sort direction
- **THEN** the change is rejected with 422

### Requirement: Who configures the columns

The columns of an instance catalog SHALL be changed only with `catalog:manage`, those of a personal catalog only by its creator; any other request SHALL be rejected with 403. Every change SHALL be audited with the actor.

Trace: REQ-044, ADR-032 §8, owner decision 2026-11-05

#### Scenario: Reader without catalog management

- **WHEN** a person holding only `viewer` changes the columns of an instance catalog
- **THEN** the request is rejected with 403 and the columns stay unchanged

#### Scenario: Own personal catalog

- **WHEN** Kurt changes the columns of his personal catalog
- **THEN** the columns are saved and the audit trail names Kurt
