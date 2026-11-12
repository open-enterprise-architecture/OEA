# Delta for portal/portal-catalogs

## MODIFIED Requirements

### Requirement: Visibility in the portal

The portal query SHALL apply the same property visibility as the catalog query: a column on a property the reader cannot read SHALL be returned as not readable with empty values, and soft-deleted entities SHALL NOT be returned. A sort on a property that is not a readable column SHALL be rejected. A holder of an override role SHALL read every column with all values, as in the catalog query.

Trace: REQ-160, REQ-124, REQ-126, ADR-019, owner decision 2026-11-08, US-128 AC1, REQ-128 AC1

#### Scenario: Restricted property

- **GIVEN** "Application Inventory" configures a column on `investmentCost`, which is `role-restricted` with the read role `enterprise-architect`
- **WHEN** Petra, a portal reader, queries it through the portal
- **THEN** the column has no values for her, and sorting by it answers 400 naming `sort`

#### Scenario: Deleted entity

- **WHEN** an application of "Application Inventory" is soft-deleted
- **THEN** the portal query no longer returns it and the total does not count it

#### Scenario: Override role in the portal

- **GIVEN** the override role list contains `migration-support`
- **WHEN** Petra, holding `portal-reader` and `migration-support`, queries "Application Inventory" through the portal
- **THEN** the column on `investmentCost` shows all values, and the read is audited as `property-override-access`
