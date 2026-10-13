---
id: REQ-053
title: Configure and validate PropertyAggregation DataSource
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-07
  business_objects:
    - dashboard
    - metamodel-configuration
  business_rules: []
  stakeholders:
    - SH-03
    - SH-07
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-053: Configure and validate PropertyAggregation DataSource

## Statement

The system MUST validate a `PropertyAggregationDataSource` configuration for `kpi` and `chart` widgets and, during data calculation (REQ-055), resolve it as an aggregation query on the entity store. The DataSource MUST support the following parameters: `entityType`, `propertyName`, `aggregationFn` (sum|count|avg|min|max), optional `groupBy` and optional `filters`. At write time (creating/editing a widget), the system MUST check whether `entityType` and `propertyName` exist in the current [MetamodelConfiguration](../../business-objects/metamodel-configuration.md).

## Rationale

PropertyAggregation is the core DataSource for C-level KPIs such as "Total investment costs across all plateaus". Without server-side validation at write time, widgets with invalid references could be saved and fail silently on retrieval.

## Context

`propertyName` references a [PropertyDefinition](../../business-objects/metamodel-configuration.md) (v0.6.0) with `dataType.kind` ∈ {int, varchar, enum}. The aggregation functions `sum`, `avg`, `min`, `max` are only valid for `kind=int`. `count` is always valid (counts entities regardless of the attribute type).

The special word `id` is always valid for `propertyName` with `aggregationFn=count`, even if no explicit property "id" is defined in the MetamodelConfiguration (entity-id is system-defined).

## Type-Specific Fields

### DataSource Configuration Fields

| Field | Type | Optional | Description |
|---|---|---|---|
| entityType | string | required | EntityType ID (built-in or custom); must exist in MetamodelConfiguration |
| propertyName | string | required | PropertyDefinition.name on entityType; `id` always valid for count |
| aggregationFn | enum | required | `sum`, `count`, `avg`, `min`, `max` |
| groupBy | string | optional | PropertyDefinition.name on entityType OR `architectureDomainId` OR `architectureLayerId` |
| filters | FilterExpression[] | optional | Same filter syntax as catalog filters (REQ-047); only attributes of the entityType; no join |

### Validation Rules at Write Time

| Rule-ID | Statement | Trigger |
|---|---|---|
| BR-01 | `entityType` must exist as a valid type in MetamodelConfiguration (scope=instance) | onCreate, onUpdate |
| BR-02 | `propertyName` must exist as `PropertyDefinition.name` on `entityType`; exception: `id` always valid | onCreate, onUpdate |
| BR-03 | `aggregationFn` ∈ {sum, avg, min, max} requires `propertyName` with `dataType.kind=int`; on violation → 422 | onCreate, onUpdate |
| BR-04 | `groupBy` (if set) must be a PropertyDefinition.name on entityType OR one of the system values `architectureDomainId`, `architectureLayerId` | onCreate, onUpdate |
| BR-05 | If an EntityType or a PropertyDefinition is removed from the metamodel (UC-04), affected widgets are not deleted automatically; on the next data retrieval (REQ-055) they return an error status instead of data | onMetamodelChange |

### Output During Data Calculation (REQ-055)

**Without groupBy** (KPI widget):
```json
{ "value": 42500000 }
```

**With groupBy** (chart widget):
```json
{
  "series": [
    { "group": "Plateau 2026", "value": 12000000 },
    { "group": "Plateau 2027", "value": 30500000 }
  ]
}
```

**With groupBy=architectureDomainId** (pie chart by domain):
```json
{
  "series": [
    { "group": "finance", "groupLabel": "Finance", "value": 18 },
    { "group": "hr", "groupLabel": "Human Resources", "value": 7 }
  ]
}
```
`groupLabel` is resolved from `ArchitectureDomainDefinition.name`.

## Acceptance Criteria

**AC1** (Valid int aggregation, no groupBy):
- Given: EntityType `plateau` with PropertyDefinition `investitionskostenPrognose` (kind=int); 3 plateau instances with values 5000, 12000, 25000
- When: DataSource configured with entityType=plateau, propertyName=investitionskostenPrognose, aggregationFn=sum
- Then: data calculation returns `{ "value": 42000 }`

**AC2** (Valid count aggregation with groupBy):
- Given: EntityType `application-component`; 20 entities, of which 12 domain=finance, 8 domain=hr
- When: DataSource with propertyName=id, aggregationFn=count, groupBy=architectureDomainId
- Then: series=[{group: "finance", value: 12}, {group: "hr", value: 8}]

**AC3** (Invalid propertyName for sum):
- Given: PropertyDefinition `name` (kind=varchar) on EntityType `plateau`
- When: Create widget with aggregationFn=sum, propertyName=name
- Then: HTTP 422 "sum/avg/min/max require propertyName with dataType.kind=int"

**AC4** (Invalid entityType):
- When: Create widget with entityType=non-existent-type
- Then: HTTP 422 "EntityType 'non-existent-type' not found in MetamodelConfiguration"

**AC5** (No entities present):
- Given: EntityType `plateau` has no instances in the repository
- When: Data calculation with aggregationFn=sum
- Then: `{ "value": null }` (no error; null means "no data")

**AC6** (groupBy=architectureLayerId):
- Given: 3 EntityTypes each assigned to a layer; 50 entities distributed
- When: propertyName=id, aggregationFn=count, groupBy=architectureLayerId
- Then: series with entries per layer ID and their entity count

## Dependencies

- Obtains validation information from: MetamodelConfiguration v0.6.0 (PropertyDefinition, ArchitectureLayers, ArchitectureDomains)
- Used by: REQ-052 (widget validation), REQ-055 (data calculation)
- Filter syntax analogous to: REQ-047 (catalog filter)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; BR-01–05, output format with/without groupBy, groupBy special values |
