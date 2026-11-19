---
id: REQ-053
title: Configure and validate PropertyAggregation DataSource
type: functional
priority: should
status: proposed
version: 0.2.0
created: 2026-06-26
last_updated: 2026-11-19
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

The system MUST validate a `PropertyAggregationDataSource` configuration (contract field `aggregation`) for `kpi` and `chart` widgets and, during data calculation (REQ-055), resolve it as an aggregation query on the entity store. The DataSource MUST support the following parameters: `metatypeId`, `propertyDefId`, `function` (count|sum|avg|min|max) and, for charts, `groupBy`. At write time (creating/editing a widget), the system MUST check against the current metamodel that `metatypeId` is an element metatype and that `propertyDefId` and a grouping property are mapped to it. Optional `filters` are not supported yet.

**Implementation note (change us-056-kpi-and-chart-widgets, S17)**: the metamodel is referenced by IDs, not names. `metatypeId` includes the subtypes of the metatype; `null` is allowed only for `count` without a property and counts all element entities. Only entities that are not deleted in the catalog are counted, not the deltas of solutions. `groupBy` is `{kind, propertyDefId?}` with the kind `property` (a mapped property of data type `enum`, `string`, `boolean` or `reference`; a reference groups by the name of the referenced entity), `metatype` or `layer` (the architecture layer of the metatype). Grouping by an architecture domain follows when domains exist; grouping by the entity name is not offered. At most 50 groups are shown by value descending, the rest is aggregated with the same function into "Other", entities without a value form "(none)". An aggregation or grouping over a property whose values the reader may not read for every entity (role-restricted without a read role, or connection-scoped) gives the widget the error `restricted`; override roles do not lift it ([REQ-056](./REQ-056-dashboard-access-visibility.md), ADR-032).

## Rationale

PropertyAggregation is the core DataSource for C-level KPIs such as "Total investment costs across all plateaus". Without server-side validation at write time, widgets with invalid references could be saved and fail silently on retrieval.

## Context

`propertyDefId` references a [PropertyDefinition](../../business-objects/metamodel-configuration.md) with data type `integer`, `string`, `boolean`, `enum`, `reference` and others. The aggregation functions `sum`, `avg`, `min`, `max` are only valid for `integer`. `count` counts the entities, or, with a property, the entities that have a value of it.

## Type-Specific Fields

### DataSource Configuration Fields

| Field | Type | Optional | Description |
|---|---|---|---|
| metatypeId | UUID | conditional | element metatype of the instance metamodel; null only for `count` without `propertyDefId` |
| propertyDefId | UUID | conditional | property mapped (effective now) to the metatype or a base type; null counts the entities |
| function | enum | required | `count`, `sum`, `avg`, `min`, `max` |
| groupBy | object | conditional | charts only (required there): `{kind: property\|metatype\|layer, propertyDefId?}` |

### Validation Rules at Write Time

| Rule-ID | Statement | Trigger |
|---|---|---|
| BR-01 | `metatypeId` must be an existing element metatype of the instance metamodel; 422 naming `metatypeId` | onCreate, onUpdate |
| BR-02 | `propertyDefId` must be mapped to the metatype (or a base type); 422 naming `propertyDefId` | onCreate, onUpdate |
| BR-03 | `function` ∈ {sum, avg, min, max} requires a property of data type `integer`; on violation → 422 "sum requires an integer property" | onCreate, onUpdate |
| BR-04 | `groupBy` of a chart must be a mapped property of data type enum, string, boolean or reference, the metatype, or the layer; a KPI must have none; 422 naming `groupBy` | onCreate, onUpdate |
| BR-05 | If a metatype or property is removed from the metamodel or no longer fits (UC-04), affected widgets are not deleted automatically; on the next data retrieval (REQ-055) they return the error `invalid-configuration` instead of data; a deleted layer moves its metatypes into the group "(none)" | onMetamodelChange |

### Output During Data Calculation (REQ-055)

**KPI widget**: `{"value": 42500000}`; `null` for sum, avg, min and max over no values, 0 for count over no entities.

**Chart widget**:

```json
{
  "series": [
    { "kind": "value", "key": "P3", "label": "P3", "labelKey": "enum-value.<id>.label", "value": 25000000 },
    { "kind": "none", "value": 1000000 }
  ]
}
```

`kind` is `value`, `none`, `other`, `true` or `false`; the client translates `labelKey` with the label bundle (`enum-value.{id}.label`, `metatype.{id}.name`, `architecture-layer.{id}.name`) with `label` as fallback and names "(none)", "Other", "Yes" and "No" itself.

## Acceptance Criteria

**AC1** (Valid int aggregation, no groupBy):

- Given: element type "Application" with the integer property "Investment"; 3 applications with values 5 000 000, 12 000 000, 25 000 000 and a fourth one that is deleted
- When: DataSource configured with that metatype, property and function=sum
- Then: data calculation returns `{ "value": 42000000 }`

**AC2** (Count with grouping):

- Given: element type "Application" with the enum property "Plateau"; 12 applications with P1, 8 with P2
- When: DataSource with function=count, groupBy=property "Plateau"
- Then: series=[{value, label P1, 12}, {value, label P2, 8}]

**AC3** (Invalid property for sum):

- Given: a property of data type `string` mapped to the element type
- When: Create widget with function=sum on it
- Then: HTTP 422 "sum requires an integer property"

**AC4** (Invalid metatype):

- When: Create widget with a metatype ID that does not exist or is a connection metatype
- Then: HTTP 422 naming `metatypeId`

**AC5** (No entities present):

- Given: no entity has a value of the property
- When: Data calculation with function=sum
- Then: `{ "value": null }` (no error; null means "no data")

**AC6** (groupBy=layer):

- Given: 3 layers with element types each; 50 entities distributed
- When: function=count over all element entities, groupBy=layer
- Then: series with one entry per layer, named after the layer, summing to 50; element types without a layer form "(none)"

**AC7** (more than 50 groups):

- Given: 51 distinct values of the grouping property
- When: the chart is calculated
- Then: 50 groups and "Other" with the value of the last group

**AC8** (restricted property):

- Given: the property is readable only for a role the reader does not hold
- When: the widget is calculated
- Then: the widget has the error `restricted`; a holder of an override role without the read role gets the same

## Dependencies

- Obtains validation information from: MetamodelConfiguration (PropertyDefinition, mappings, architecture layers); architecture domains follow
- Used by: REQ-052 (widget validation), REQ-055 (data calculation)
- Filters (analogous to REQ-047, catalog filter): not part of the implementation yet

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; BR-01–05, output format with/without groupBy, groupBy special values |
| 0.2.0 | 2026-11-19 | Fullstack Engineer | us-056-kpi-and-chart-widgets: realigned to the implementation: metamodel IDs, groupBy kinds, limit of 50 groups, `restricted` and `invalid-configuration`; no domains and no filters yet |
