# Delta for catalog/catalog-filters

## ADDED Requirements

### Requirement: Filter on a connection-scoped column

A filter on a column of a `connection-scoped` property SHALL consider only the values of rows in the caller's scope; other rows SHALL count as without value.

Trace: REQ-127 AC4, REQ-160 AC2

#### Scenario: Value outside the scope

- **GIVEN** "CRM Sales" is classified `secret` but not assigned to Michael's element
- **WHEN** Michael filters by `securityClassification:eq:secret`
- **THEN** "CRM Sales" is not returned
