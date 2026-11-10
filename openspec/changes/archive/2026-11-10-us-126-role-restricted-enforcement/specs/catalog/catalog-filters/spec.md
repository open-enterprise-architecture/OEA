# Delta for catalog/catalog-filters

## ADDED Requirements

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
