# catalog/catalog-views Specification

## Purpose

Defines the saved views of a catalog: what a view keeps of the view state, how it is validated, saved, marked as default and removed, who may change views, how the query shows the columns of a view, and how the catalog page applies a view and the default view.

## Requirements

### Requirement: Save a view

A catalog SHALL keep named views, each with a column selection and order of the catalog's columns, 0–20 filter expressions with their combination, join mode overrides, an optional sort and a default flag. Saving SHALL store a snapshot of these values; a view SHALL NOT reference saved filters. A catalog SHALL have at most 20 views; every change SHALL be audited.

Trace: US-053 AC1, AC4, REQ-048 AC1, owner decision 2026-11-06

#### Scenario: Compact view

- **GIVEN** the catalog "Application Inventory" with the columns name, status and vendor, the filter "Status equals active" and the join "Interfaces"
- **WHEN** Kurt saves the view "Compact view" with the columns name and status and the filter
- **THEN** the catalog offers "Compact view" with these columns and this filter

#### Scenario: Join mode in a view

- **WHEN** Kurt saves the view "Detail view" with the join "Interfaces" in rows mode
- **THEN** activating "Detail view" shows the join "Interfaces" in rows mode

### Requirement: Validation of a view

Saving SHALL be rejected with 422 naming the field when the name is blank, longer than 100 characters or already used in the catalog regardless of case, when the column list is empty or names a column the catalog does not show, or when the catalog already has 20 views.

Trace: US-053, REQ-048 AC3, catalog BR-05

#### Scenario: Unknown column

- **WHEN** a view names the column `unknown`
- **THEN** the change is rejected with 422 naming `columnOrder[1]`

### Requirement: Validation of the view state

Saving SHALL be rejected with 422 naming the field when a filter names no column of the view, an operator not allowed for it or an invalid value, when the sort names no sortable column of the view or of a join in rows mode, or when a join mode override names no available join.

Trace: US-053 AC4, REQ-048, catalog BR-04, BR-12

#### Scenario: Filter on a column outside the view

- **WHEN** a view with the columns name and status filters by vendor
- **THEN** the change is rejected with 422 naming `filters[0].attributePath`

### Requirement: Default view

At most one view of a catalog SHALL be the default. Marking a view as default SHALL clear the default of every other view; removing the default view SHALL leave the catalog without a default.

Trace: US-053 AC3, REQ-048 AC2, AC6, catalog BR-06

#### Scenario: Default switch

- **GIVEN** "Compact view" is the default
- **WHEN** Kurt marks "Full view" as default
- **THEN** "Full view" is the default and "Compact view" is not

#### Scenario: Remove the default view

- **WHEN** Kurt removes the default view
- **THEN** the catalog has no default view

### Requirement: Columns of a view in the query

With a view, the query SHALL return only the columns of the view, in its order, and SHALL accept filters and a sort only on these columns and on join columns. An unknown or unavailable view SHALL be rejected with 400 naming `view`. The view SHALL change nothing stored.

Trace: US-049 AC6, REQ-048 AC4, owner decision 2026-11-06

#### Scenario: Columns of the compact view

- **WHEN** the catalog is queried with the view "Compact view"
- **THEN** the result has the columns name and status

#### Scenario: Unknown view

- **WHEN** the query names a view the catalog does not have
- **THEN** the response is 400 naming `view`

### Requirement: Use a view

The catalog page SHALL offer the views of the catalog to every reader. Choosing a view SHALL replace the columns, filters, join modes and sort of the page with those of the view; the view SHALL be part of the URL (ADR-044). Choosing "All columns" SHALL leave the view.

Trace: US-053 AC2, AC4, ADR-044

#### Scenario: Switch to the full view

- **GIVEN** the catalog has the views "Compact view" and "Full view"
- **WHEN** Franz chooses "Full view"
- **THEN** the table shows the columns and filters of "Full view", and a reload shows the same view

### Requirement: Default view on opening

When the catalog opens without view state in the URL, the page SHALL apply the default view, if there is one and it is available. A URL with its own view state SHALL keep it. Without a default view, the page SHALL show the configured columns, no filter and the join modes of the configuration.

Trace: US-049 AC6, US-053 AC1, AC2, AC5, REQ-048 AC4, AC5, owner decision 2026-11-06

#### Scenario: Default view applied

- **GIVEN** the default view "Compact view" with the columns name and status and the filter "Status equals active"
- **WHEN** Franz opens the catalog
- **THEN** the table shows only the columns name and status and only active entries, and the filter bar shows "Status equals active"

#### Scenario: Shared link keeps its state

- **GIVEN** the default view "Compact view"
- **WHEN** Franz opens a link to the catalog sorted by name descending
- **THEN** the table shows the configured columns sorted by name descending

#### Scenario: No default view

- **GIVEN** the catalog has no default view
- **WHEN** Max opens the catalog
- **THEN** all configured columns are visible and no filter is set

### Requirement: Availability of a view

A view SHALL be marked unavailable when it names a column, a join or a sort column the catalog no longer has; the page SHALL offer it disabled, and the query SHALL reject it. Removing a join SHALL remove the join mode override, the filters and the sort of every view on that join.

Trace: REQ-048, catalog BR-05, BR-10

#### Scenario: Column removed

- **GIVEN** the view "Compact view" with the column status
- **WHEN** Kurt removes the column status from the catalog
- **THEN** "Compact view" is offered as not available

### Requirement: Who changes views

The views of an instance catalog SHALL be changed only with `catalog:manage`, those of a personal catalog only by its creator; any other request SHALL be rejected with 403. A reader without that right SHALL see no "Save view" action.

Trace: US-053, REQ-048, ADR-032 §8

#### Scenario: Reader of an instance catalog

- **WHEN** Franz, holding only `viewer`, saves a view in an instance catalog through the API
- **THEN** the request is rejected with 403
