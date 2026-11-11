# Delta for catalog/catalog-query

## MODIFIED Requirements

### Requirement: Visibility of the query result

Querying a catalog SHALL require `entity:read`; another person's personal catalog SHALL be answered with 404, like a catalog that does not exist. Soft-deleted entities SHALL NOT be returned. A column on a property the caller cannot read SHALL be returned as not readable and not sortable, with empty values in every row and every join result, and the totals SHALL not depend on it.

Trace: US-049, REQ-043 AC4, ADR-019, ADR-032, US-126 AC4, REQ-160 AC2, #68

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
- **WHEN** Kurt, whose account has no link, queries a catalog with a column on it
- **THEN** the column is not readable and has no values

## ADDED Requirements

### Requirement: Connection-scoped values per row

For a caller whose account is linked, a column on a `connection-scoped` property SHALL be readable, and each row SHALL show the value only if its entity is connected to the caller's element by a connection of the scoping type, in either direction. Sort and filter SHALL treat the values of other rows as empty.

Trace: US-127 AC1–AC3, REQ-127 AC1, AC2, AC4, #68, owner decision 2026-11-10

#### Scenario: Own domain and other domain

- **GIVEN** `securityClassification` is `connection-scoped` with `DomainAssignment`, and Michael's element is assigned to "ERP Warehouse" but not to "CRM Sales"
- **WHEN** Michael queries a catalog with a column on it
- **THEN** "ERP Warehouse" shows its classification and "CRM Sales" an empty cell

#### Scenario: Sort and filter in scope only

- **WHEN** Michael sorts or filters by `securityClassification`
- **THEN** "CRM Sales" sorts like an entry without value and never matches a filter on a value

#### Scenario: Assignment removed

- **WHEN** the `DomainAssignment` of "ERP Warehouse" is deleted and Michael queries again
- **THEN** "ERP Warehouse" shows an empty cell
