# Delta for catalog/catalog-columns

## MODIFIED Requirements

### Requirement: All attributes as columns

The configuration SHALL offer every system field and every property mapped to the primary metatype that is not retired, including inherited mappings and properties whose visibility is not `public`. A connection catalog SHALL also offer them for its source and target: the properties of the side's allowed endpoint metatypes and their subtypes, or all properties without a constraint. An unknown, unoffered or duplicate attribute SHALL be rejected with 422. Whether a reader sees the values of a column is decided by the query.

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
