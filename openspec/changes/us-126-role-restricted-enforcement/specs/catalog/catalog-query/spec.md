# Delta for catalog/catalog-query

## MODIFIED Requirements

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
