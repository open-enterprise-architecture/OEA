---
identifier: dashboard
name_de: Dashboard
name_en: Dashboard
version: 0.2.0
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

**Implemented in change us-055-dashboards**: the dashboard, the widget types `text` and `table`, the data source `CatalogQueryDataSource` without `additionalFilters`, and the grid. KPI and chart widgets, `PropertyAggregationDataSource`, `globalFilters` and the column overrides follow later (S17 and beyond); the service answers 422 for the types `kpi` and `chart`.

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
| dataSource | PropertyAggregationDataSource | required | | | Data source; must contain aggregationFn |
| unit | string | optional | | max. 20 characters | Unit (e.g. "CHF", "EUR m", "%") |
| comparisonMode | enum | optional | `none` | `[none, vs-previous-value, vs-target]` | Comparison mode for the trend arrow |
| targetValue | decimal | conditional | | only if comparisonMode=vs-target | Target value |
| numberFormat | string | optional | | e.g. "#,##0" | Number format (locale-sensitive) |

### ChartWidget

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| chartType | enum | required | | `[bar, line, pie, donut, area, treemap]` | Chart type |
| dataSource | CatalogQueryDataSource \| PropertyAggregationDataSource | required | | | Data source |
| xAxis | AxisConfig | conditional | | not for chartType=pie/donut/treemap | X-axis configuration |
| yAxis | AxisConfig | conditional | | min. 1 for bar/line/area; exactly 1 for pie/donut | Y-axis configuration |
| colorScheme | string | optional | `default` | | Color palette |
| showLegend | boolean | optional | true | | Show legend |
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

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| entityType | string | required | | valid EntityType ID (built-in or custom) | Entity type over which to aggregate |
| propertyName | string | required | | must exist as a PropertyDefinition on entityType; dataType.kind must be `int` for sum/avg/min/max | Aggregated attribute |
| aggregationFn | enum | required | | `[sum, count, avg, min, max]` | Aggregation function |
| groupBy | string | optional | null | valid PropertyName or `architectureLayerId` or `architectureDomainId` | Grouping field; null = no grouping (a single result) |
| filters | FilterExpression[] | optional | [] | same syntax as catalog filters | Pre-filters on the entities |

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
| BR-03 | `PropertyAggregationDataSource.propertyName` must be a valid `PropertyDefinition` on the referenced `entityType` | onCreate, onUpdate | – |
| BR-04 | `PropertyAggregationDataSource.aggregationFn` = `sum` / `avg` / `min` / `max` requires `propertyName` with `dataType.kind=int` | onCreate, onUpdate | – |
| BR-05 | `ChartWidget` with `chartType=pie` or `chartType=donut` may have exactly one Y-axis | onCreate, onUpdate | – |
| BR-06 | `KPIWidget` with `comparisonMode=vs-target` must have `targetValue` set | onCreate, onUpdate | – |
| BR-07 | `GridPosition.col + GridPosition.width` must not exceed 13 | onCreate, onUpdate | – |
| BR-08 | Dashboards with `scope=personal` are not visible to other users (answered as not existing, 404) and cannot be shared via sharing links; holders of `settings:manage` read them, read-only | onRead | – |

## Example: Investment Cost Dashboard for the CIO

```yaml
dashboard:
  name: "IT Investment Planning 2026–2030"
  scope: instance
  globalFilters: []
  widgets:
    - widgetType: kpi
      title: "Total Investment Costs (Forecast)"
      dataSource:
        entityType: plateau
        propertyName: investmentCostForecast
        aggregationFn: sum
      unit: "CHF m"
      gridPosition: { col: 1, row: 1, width: 3, height: 1 }

    - widgetType: chart
      title: "Investment Costs per Plateau"
      chartType: bar
      dataSource:
        entityType: plateau
        propertyName: investmentCostForecast
        aggregationFn: sum
        groupBy: name
      xAxis: { propertyName: name, label: "Plateau" }
      yAxis: { propertyName: investmentCostForecast, label: "CHF" }
      gridPosition: { col: 4, row: 1, width: 9, height: 3 }

    - widgetType: chart
      title: "Entities by Architecture Domain"
      chartType: pie
      dataSource:
        entityType: application-component
        propertyName: id
        aggregationFn: count
        groupBy: architectureDomainId
      yAxis: { propertyName: id, label: "Count" }
      gridPosition: { col: 1, row: 2, width: 6, height: 3 }
```

**Precondition**: The plateau EntityType (or a stereotype `ManagedPlateau`) must have `PropertyDefinition { name: investmentCostForecast, dataType: { kind: int }, validationMode: warning, category: "Costs" }` defined.

## Delimitation from Similar Objects

- **NOT** [catalog](./catalog.md): A catalog is a tabular row view without aggregation; a dashboard aggregates, visualizes, and condenses into metrics and charts. Dashboards optionally use catalogs as a data source.
- **NOT** report / report export: A report is a point-in-time export (PDF, Excel) for archiving and distribution; a dashboard is live and interactive in the Web Portal.
- **NOT** diagram / canvas: Diagrams show architecture notation (ArchiMate entities, connections); dashboards show business metrics in standard chart types.
- **NOT** [viewpoint](./viewpoint.md): A viewpoint controls which EntityTypes may appear in an architecture diagram; a dashboard is a reporting view without modeling function.

## Notes

- 2026-11-18 (us-055-dashboards): `docs/data-model.puml` has the tables `dashboards` and `dashboard_widgets` (V43). The configuration of a widget is stored as JSONB (`text`: `{content}`; `table`: `{catalogId, savedViewId?, pageSize}`) and checked by the service; the grid checks (BR-07 and the limits of `GridPosition`) also hold in the database. `owner` replaces `createdBy` (one person, the creator).

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Business Engineer | Initial draft: KPI, chart, table, text widgets; CatalogQuery and PropertyAggregation data sources; C-level investment cost example |
| 0.2.0 | 2026-11-18 | Fullstack Engineer | us-055-dashboards: owner, version and limits; widget types of the first implementation; scope rules and visibility for administrators; names compared ignoring case |
