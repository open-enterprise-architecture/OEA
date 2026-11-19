---
identifier: dashboard
name_de: Dashboard
name_en: Dashboard
version: 0.3.0
status: draft
maturity: initial
owner_role: Lead Enterprise Architect
aliases:
  - C-Level-Dashboard
  - Auswertungs-Dashboard
related_capabilities: []
references:
  - concept: concept/70-platform/21-api-architecture.md
---

# Business Object: Dashboard

## Definition

A `Dashboard` is a configurable view in the **Web Portal** that combines several widgets into a coherent C-level report. Widgets visualize aggregated metrics, cost forecasts, and distributions from the architecture repository.

## Description

Dashboards primarily target C-level decision makers (CIO, division heads) who do not read raw data from catalogs but need condensed metrics and trends. A typical example is the evaluation of investment cost forecasts across several plateaus to support budget decisions.

**Data sources**: Widgets obtain their data from two sources:

1. **CatalogQueryDataSource**: uses an existing [catalog](./catalog.md) including its joins, columns, and filters as the data basis
2. **PropertyAggregationDataSource**: aggregates [PropertyDefinitions](./metamodel-configuration.md) directly across entities of a given type – without a catalog as an intermediate layer; supports groups and aggregation functions

**Scope**: Dashboards have the same scope mechanism as catalogs – `instance` (visible to all) or `personal` (only the creator). Shared dashboards are changed by every holder of `dashboard:manage`; a personal dashboard is changed by its owner only. Holders of `settings:manage` read the personal dashboards of other persons, read-only (owner decisions 2026-11-18, ADR-032 amendment). A personal dashboard can become shared (not the other way round) unless a widget shows a personal catalog.

**Implemented in change us-055-dashboards**: the dashboard, the widget types `text` and `table`, the data source `CatalogQueryDataSource` without `additionalFilters`, and the grid.

**Implemented in change us-056-kpi-and-chart-widgets**: the widget types `kpi` (comparison `none`, `vs-target` and, from change us-056-kpi-previous-value, `vs-previous-value`) and `chart`, and the `PropertyAggregationDataSource` as described under "PropertyAggregationDataSource" below, with metamodel IDs instead of names, grouping by property, metatype or architecture layer, at most 50 groups, and the visibility rules of the aggregate. `globalFilters`, `filters` of a data source, architecture domains and the column overrides follow later.

## Attributes

### Dashboard (root object)

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | UUID v4 | Primary key |
| name | string | required | | 1–200 characters after trimming; unique per scope, ignoring case | Name of the dashboard |
| description | string | optional | | max. 1000 characters | Purpose and target audience |
| scope | enum | required | `instance` | `[instance, personal]` | `instance` = visible to all; `personal` = creator only |
| owner | reference | required | | target: person | Creator and owner; only persons own dashboards (service accounts have no access) |
| version | integer | required | 1 | ≥ 1 | Raised by every change of the dashboard, its widgets or its layout; the `ETag` and the `If-Match` of every write |
| instanceId | reference | required | | target: instance | Link to the OEA instance |
| widgets | Widget[] | required | [] | 0–50 widgets (a new dashboard has none) | Contained widgets |
| globalFilters | FilterExpression[] | optional | [] | same syntax as catalog filters | Filters that restrict all data-driven widgets |

### Widget (abstract base)

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | UUID v4, unique within the dashboard | Internal widget ID |
| widgetType | enum | required | | `[kpi, chart, table, text]` | Kind of widget |
| title | string | required | | max. 150 characters | Display title |
| gridPosition | GridPosition | required | | | Position in the dashboard grid (column, row, width, height) |

### KPIWidget

Displays a single metric prominently (number + unit, optionally with a comparison value).

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| dataSource | PropertyAggregationDataSource | required | | | Data source (`aggregation` in the contract); `groupBy` must be empty |
| unit | string | optional | | max. 20 characters | Unit (e.g. "CHF", "EUR m", "%") |
| comparisonMode | enum | optional | `none` | `[none, vs-previous-value, vs-target]` | Comparison mode for the trend (arrow icon and text); `vs-previous-value` compares with the value of the same aggregation 30 days ago (change us-056-kpi-previous-value) |
| targetValue | decimal | conditional | | only if comparisonMode=vs-target | Target value |
| numberFormat | string | optional | | e.g. "#,##0" | Number format (locale-sensitive); not implemented, the value is shown in the number format of the user interface language |

### ChartWidget

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| chartType | enum | required | | `[bar, line, pie, donut, area, treemap]` | Chart type |
| dataSource | PropertyAggregationDataSource | required | | | Data source with `groupBy`; a chart shows one series per widget, so there is one value axis |
| unit | string | optional | | max. 20 characters | Unit in tooltips and data labels |
| xAxis | AxisConfig | conditional | | | Not implemented: the axes follow from the grouping and the aggregated value |
| yAxis | AxisConfig | conditional | | | Not implemented, see `xAxis` |
| colorScheme | string | optional | `default` | | Not implemented: the palette follows the light or dark theme |
| showLegend | boolean | optional | true | | Show legend (not for the treemap) |
| showDataLabels | boolean | optional | false | | Values directly on the chart |

### TableWidget

Displays catalog results directly in the dashboard (read-only, no configuration).

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| dataSource | CatalogQueryDataSource | required | | | Data source; must point to an existing catalog |
| pageSize | integer | optional | 10 | 5–100 | Rows per page |
| columnOverrides | ColumnOverride[] | optional | [] | | Override for column title or width in the dashboard context |

### TextWidget

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| content | string | required | | max. 10 000 characters; Markdown | Free text with Markdown formatting; no data binding |

### CatalogQueryDataSource

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| catalogId | reference | required | | target: catalog | Referenced catalog including joins and columns |
| savedViewId | reference | optional | | target: SavedView in the referenced catalog | Preselection of a SavedView; null = default view of the catalog |
| additionalFilters | FilterExpression[] | optional | [] | | Further filters on top of the catalog filters |

### PropertyAggregationDataSource

As implemented in change us-056-kpi-and-chart-widgets (contract `WidgetAggregation`); names of the earlier draft are replaced by IDs of the metamodel.

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| metatypeId | reference | conditional | | an element metatype of the instance metamodel; null only for `count` without `propertyDefId` | Entity type over which to aggregate; its subtypes are included, entities that are deleted are not, and neither are the changes of solutions. Null counts all element entities |
| propertyDefId | reference | conditional | | a property mapped (effective now) to the metatype or a base type; `sum`, `avg`, `min`, `max` need data type `integer` | Aggregated property; null counts the entities themselves (`count` only). A `count` of a property counts the entities that have a value of it |
| function | enum | required | | `[count, sum, avg, min, max]` | Aggregation function; `avg` keeps decimals, `sum`, `min`, `max` over no values and `avg` over no values give no value ("– (no data)"), `count` gives 0 |
| groupBy | GroupBy | conditional | null | charts only (required there); a KPI rejects it | Grouping field of a chart |

`GroupBy` has the `kind` `property` (a mapped property of data type `enum`, `string`, `boolean` or `reference`, named by `propertyDefId`; a reference groups by the name of the referenced entity), `metatype` (the metatype of the entity) or `layer` (the architecture layer of the metatype). Entities without a value form the group "(none)", as do metatypes without a layer. At most 50 groups are shown, ordered by value descending and then by name; the rest is aggregated with the same function into "Other". Grouping by the name of the entity and by architecture domain is not offered.

The data of a widget carries `value` and `target` (KPI) or `series` (chart): per group the `kind` (`value`, `none`, `other`, `true`, `false`), a `key`, the `label` in the corporate language and the `labelKey` of the label bundle, so that the client names enum values, metatypes and layers in the language of the user and names "(none)", "Other", "Yes" and "No" itself.

### AxisConfig

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| label | string | optional | | | Axis label |
| propertyName | string | required | | must be available from the data source | Data binding |
| numberFormat | string | optional | | | Number format; only for numeric axes |

### GridPosition

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| col | integer | required | | 1–12 | Start column in the 12-column grid |
| row | integer | required | | ≥ 1 | Start row |
| width | integer | required | | 1–12; col+width ≤ 13 | Column width |
| height | integer | required | | 1–6 | Row height (in grid units) |

`row` is limited to 500 (an input limit of the implementation). Overlapping widgets are accepted by the service and marked in the editor.

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| belongsTo | instance | n:1 | no | Dashboard belongs to the OEA instance |
| createdBy | [person](./person.md) | n:1 | no | Creator |
| references | [catalog](./catalog.md) | n:m | yes | Optional data source for table and chart widgets |

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | `Dashboard.name` must be unique per scope (`instance`: global; `personal`: per owner), compared case-insensitively after trimming; a taken name is rejected with 422 `dashboard-name-taken` | onCreate, onUpdate | – |
| BR-02 | `CatalogQueryDataSource.catalogId` must point to an existing catalog that is visible to the requesting user; a shared dashboard shows shared catalogs only; a deleted or invisible catalog gives an error widget on query, not an error of the dashboard | onCreate, onUpdate, onQuery | – |
| BR-03 | `PropertyAggregationDataSource.propertyDefId` must be mapped to the referenced `metatypeId` (or a base type) and `metatypeId` must be an existing element metatype | onCreate, onUpdate, onQuery | REQ-053 |
| BR-04 | `PropertyAggregationDataSource.function` = `sum` / `avg` / `min` / `max` requires `propertyDefId` with data type `integer` (422 "sum requires an integer property") | onCreate, onUpdate | REQ-053 |
| BR-05 | A `ChartWidget` has exactly one series, hence one value axis; a pie or donut has no axes | onCreate, onUpdate | – |
| BR-06 | `KPIWidget` with `comparisonMode=vs-target` must have `targetValue` set | onCreate, onUpdate | – |
| BR-07 | `GridPosition.col + GridPosition.width` must not exceed 13 | onCreate, onUpdate | – |
| BR-08 | Dashboards with `scope=personal` are not visible to other users (answered as not existing, 404) and cannot be shared via sharing links; holders of `settings:manage` read them, read-only | onRead | – |
| BR-09 | An aggregation or grouping over a property whose values the reader may not read for every entity (role-restricted without a read role, or connection-scoped) gives the widget the error `restricted`; override roles do not lift it. Counting and grouping by metatype or layer are always readable | onQuery | ADR-032, REQ-056 |
| BR-10 | A widget whose metatype or property no longer exists or no longer fits gives the error `invalid-configuration` on read; it is not deleted | onQuery | REQ-053 BR-05 |

## Example: Investment Cost Dashboard for the CIO

```yaml
dashboard:
  name: "IT Investment Planning 2026–2030"
  scope: instance
  widgets:
    - widgetType: kpi
      title: "Total Investment Costs (Forecast)"
      dataSource:                       # contract: config.aggregation
        metatypeId: <Application Component>
        propertyDefId: <Investment>     # data type integer
        function: sum
      unit: "CHF"
      comparison: vs-target
      targetValue: 50000000
      gridPosition: { col: 1, row: 1, width: 4, height: 2 }

    - widgetType: chart
      title: "Investment Costs per Plateau"
      chartType: bar
      dataSource:
        metatypeId: <Application Component>
        propertyDefId: <Investment>
        function: sum
        groupBy: { kind: property, propertyDefId: <Plateau> }   # an enum property
      unit: "CHF"
      showLegend: true
      showDataLabels: true
      gridPosition: { col: 5, row: 1, width: 8, height: 3 }

    - widgetType: chart
      title: "Entities by Architecture Layer"
      chartType: pie
      dataSource:
        metatypeId: null                # all element entities
        propertyDefId: null
        function: count
        groupBy: { kind: layer }
      gridPosition: { col: 1, row: 3, width: 4, height: 3 }
```

**Precondition**: the metatype has the properties "Investment" (data type `integer`) and "Plateau" (data type `enum`) mapped.

## Delimitation from Similar Objects

- **NOT** [catalog](./catalog.md): A catalog is a tabular row view without aggregation; a dashboard aggregates, visualizes, and condenses into metrics and charts. Dashboards optionally use catalogs as a data source.
- **NOT** report / report export: A report is a point-in-time export (PDF, Excel) for archiving and distribution; a dashboard is live and interactive in the Web Portal.
- **NOT** diagram / canvas: Diagrams show architecture notation (ArchiMate entities, connections); dashboards show business metrics in standard chart types.
- **NOT** [viewpoint](./viewpoint.md): A viewpoint controls which EntityTypes may appear in an architecture diagram; a dashboard is a reporting view without modeling function.

## Notes

- 2026-11-19 (us-056-kpi-previous-value): no schema change; the comparison `vs-previous-value` is computed on each request from the entity history (`entity_property_value_history`, `entities.created_at` and `deleted_at`) and adds `previousValue` and `previousAt` to the widget data; nothing is stored in the dashboard.
- 2026-11-19 (us-056-kpi-and-chart-widgets): no schema change; the JSONB configuration of `kpi` is `{dataSource, aggregation, unit?, comparison, targetValue?}` and that of `chart` is `{dataSource, aggregation, unit?, chartType, showLegend, showDataLabels}` (aggregation as above). The metamodel IDs are soft references, checked at write and on each read.
- 2026-11-18 (us-055-dashboards): `docs/data-model.puml` has the tables `dashboards` and `dashboard_widgets` (V43). The configuration of a widget is stored as JSONB (`text`: `{content}`; `table`: `{catalogId, savedViewId?, pageSize}`) and checked by the service; the grid checks (BR-07 and the limits of `GridPosition`) also hold in the database. `owner` replaces `createdBy` (one person, the creator).

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Business Engineer | Initial draft: KPI, chart, table, text widgets; CatalogQuery and PropertyAggregation data sources; C-level investment cost example |
| 0.2.0 | 2026-11-18 | Fullstack Engineer | us-055-dashboards: owner, version and limits; widget types of the first implementation; scope rules and visibility for administrators; names compared ignoring case |
| 0.3.0 | 2026-11-19 | Fullstack Engineer | us-056-kpi-and-chart-widgets: KPI and chart widgets, the property aggregation with metamodel IDs, grouping by property, metatype or layer, the limit of 50 groups, aggregates and property visibility, errors after metamodel changes |
