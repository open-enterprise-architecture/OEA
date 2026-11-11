# catalog/catalog-filters Specification

## Purpose

Defines the filters of a catalog: ad-hoc filters a reader sets on the columns of the result, how they combine and act on join columns, how they are kept in the URL, and the saved filters a catalog offers for quick selection.

## Requirements

### Requirement: Filter on a column

A reader SHALL filter the result by a visible column with an operator and a value; the table SHALL show only matching rows, and the total SHALL count only them. Filters SHALL apply before sorting and paging and SHALL NOT change the catalog. Removing a filter SHALL show all rows again.

Trace: US-051 AC1, US-051 AC4, REQ-047, REQ-046 AC3

#### Scenario: Active applications only

- **GIVEN** a catalog of 10 applications, 4 of them with the status "active"
- **WHEN** Franz sets the filter "Status equals active"
- **THEN** the table shows the 4 active applications and a total of 4 entries

#### Scenario: Remove a filter

- **WHEN** Franz removes the filter chip "Status equals active"
- **THEN** the table shows all 10 applications again

### Requirement: Operators

The operators `eq`, `neq`, `isNull` and `isNotNull` SHALL apply to every column, `contains` and `startsWith` to text, `in` and `notIn` to enum and text columns, and `gt`, `lt`, `gte` and `lte` to number and date columns, compared by value. Text comparisons SHALL ignore case. The filter bar SHALL offer only the operators of the column's type.

Trace: US-051 AC5, REQ-047

#### Scenario: Operators for the name

- **WHEN** Franz adds a filter on the column "Name"
- **THEN** the operators equals, not equals, contains, starts with, is empty and is not empty are offered

#### Scenario: Support ending before a date

- **WHEN** a reader filters "Support End Date before 2027-01-01"
- **THEN** only applications whose support ends before 1 January 2027 are shown

### Requirement: Filter on a join column

A filter on a column of a join SHALL show only entries with at least one matching join result, and the join column SHALL list only the matching results, in both modes. A join without a filter SHALL stay unfiltered.

Trace: US-051 AC2, REQ-047 AC4, US-080 AC6, REQ-064, owner decision 2026-11-05

#### Scenario: Applications with a REST interface

- **GIVEN** "CRM System" reaches the interfaces "REST API" and "SFTP Export", "ERP" only "SFTP Export", through the join "Interfaces"
- **WHEN** Lukas sets the filter "Interfaces · Protocol equals REST"
- **THEN** the table shows only "CRM System", and its join column lists only "REST API"

#### Scenario: Data flows carrying personal data

- **GIVEN** a catalog of data flows with a join over `carries-data` to data objects with their classification
- **WHEN** a reader filters "Data objects · Classification equals PII"
- **THEN** the table shows only the data flows that carry a data object classified PII

### Requirement: Filter on source and target

In a connection catalog, the columns of the source and the target SHALL be filterable like other columns.

Trace: US-080 AC5

#### Scenario: Flows from the CRM

- **WHEN** a reader filters the catalog "Data Flow Overview" by "Source contains CRM"
- **THEN** only the data flows starting at "CRM System" are shown

### Requirement: AND and OR

Several filters SHALL combine with AND by default; with OR, an entry SHALL pass if any filter holds, where a join filter holds if one of its results matches.

Trace: US-051 AC3, US-052 AC4, REQ-047 AC5

#### Scenario: Both conditions

- **WHEN** Max sets "Status equals active" and "Vendor equals SAP"
- **THEN** only active applications from SAP are shown

#### Scenario: Either condition

- **WHEN** Max switches the same filters to OR
- **THEN** the active applications and the applications from SAP are shown

### Requirement: Filters in the URL

The filters and their combination SHALL be part of the view state in the URL (ADR-044), as `filter=<key>:<operator>[:<value>]` and `filterMode`; back navigation, reload and a shared link SHALL show the same filtered view. The query SHALL reject an unknown column, an operator not allowed for the column, or an invalid value with 400 naming `filter`; the page SHALL then drop the filters.

Trace: ADR-044, owner decision 2026-11-05

#### Scenario: Reload keeps the filter

- **GIVEN** Franz has set "Status equals active"
- **WHEN** he reloads the page
- **THEN** the filter chip and the 4 active applications are shown again

#### Scenario: Unknown column

- **WHEN** the query is called with `filter=nonexistent:eq:x`
- **THEN** the response is 400 naming `filter`

### Requirement: Save a filter

The filters of the bar SHALL be savable as a named saved filter of the catalog, with an optional description, the operator and 1–20 expressions; it SHALL appear in the quick selection of every reader of the catalog. Activating it SHALL replace the filters of the bar with its expressions and operator. A catalog SHALL have at most 50 saved filters; each change SHALL be audited.

Trace: US-052 AC1, US-052 AC2, US-052 AC4, REQ-047 AC1, AC2, AC5

#### Scenario: Active systems only

- **GIVEN** Kurt has set "Status equals active"
- **WHEN** he saves the filter as "Active systems only"
- **THEN** the catalog offers "Active systems only" in the quick selection

#### Scenario: Activate a saved filter

- **WHEN** Franz chooses "Active systems only" in the quick selection
- **THEN** the bar shows "Status equals active" and the table only active applications

### Requirement: Validation of a saved filter

Saving SHALL be rejected with 422 naming the field when the name is blank, longer than 100 characters or already used in the catalog regardless of case, there are no or more than 20 expressions, or an expression names no column of the catalog, an operator not allowed for it, or an invalid value.

Trace: US-052, REQ-047 AC3, catalog BR-04

#### Scenario: Unknown attribute

- **WHEN** a saved filter names the attribute `nonexistent`
- **THEN** the change is rejected with 422 naming `expressions[0].attributePath`

#### Scenario: Name taken

- **WHEN** a second filter is saved as "active systems only"
- **THEN** the change is rejected with 422 naming `name`

### Requirement: Remove a saved filter

A saved filter SHALL be removable; removing a join SHALL remove the expressions on it from the saved filters and a saved filter left without expressions.

Trace: REQ-047, owner decision 2026-11-05

#### Scenario: Remove a filter

- **WHEN** Kurt removes "Active systems only"
- **THEN** the quick selection no longer offers it

### Requirement: Who saves filters

The saved filters of an instance catalog SHALL be changed only with `catalog:manage`, those of a personal catalog only by its creator; any other request SHALL be rejected with 403. A reader without that right SHALL see no "Save filter" action and saves filters in a personal catalog of their own.

Trace: US-052 AC3, REQ-047, ADR-032 §8, owner decision 2026-11-05

#### Scenario: Reader of an instance catalog

- **WHEN** Franz, holding only `viewer`, saves a filter in an instance catalog through the API
- **THEN** the request is rejected with 403

#### Scenario: Own personal catalog

- **WHEN** Franz saves a filter in his personal catalog
- **THEN** the filter is saved and the audit trail names Franz

### Requirement: No filter on an unreadable column

A filter on a column whose property the caller cannot read SHALL be rejected with 400 naming `filter`, also on a join column and through a saved view, and the filter bar SHALL NOT offer such a column.

Trace: US-126 AC4, REQ-160 AC2, owner decision 2026-11-08

#### Scenario: Filter without read role

- **GIVEN** `investmentCost` is `role-restricted` with the read role `enterprise-architect`
- **WHEN** Anna, a viewer, queries the catalog with `filter=investmentCost:gt:100`
- **THEN** the response is 400 naming `filter`

#### Scenario: Filter bar

- **WHEN** Anna opens the filter bar of the catalog
- **THEN** the column of `investmentCost` is not offered

#### Scenario: Filter with read role

- **WHEN** Kurt, an enterprise architect, filters by `investmentCost:gt:100`
- **THEN** only entries with a cost above 100 are returned

### Requirement: Filter on a connection-scoped column

A filter on a column of a `connection-scoped` property SHALL consider only the values of rows in the caller's scope; other rows SHALL count as without value.

Trace: REQ-127 AC4, REQ-160 AC2

#### Scenario: Value outside the scope

- **GIVEN** "CRM Sales" is classified `secret` but not assigned to Michael's element
- **WHEN** Michael filters by `securityClassification:eq:secret`
- **THEN** "CRM Sales" is not returned
