---
id: REQ-052
title: Add, configure, and remove a widget
type: functional
priority: should
status: proposed
version: 0.3.0
created: 2026-06-26
last_updated: 2026-11-19
author: requirements-engineer
references:
  use_cases:
    - UC-07
  business_objects:
    - dashboard
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

# REQ-052: Add, configure, and remove a widget

## Statement

The system MUST allow those who may change a dashboard (every holder of `dashboard:manage` for a shared dashboard, the owner for a personal one) to add widgets to an existing [Dashboard](../../business-objects/dashboard.md), configure them, and remove them. Four widget types MUST be supported: `kpi`, `chart`, `table`, `text`. The widget type is immutable after creation; to change the type, the widget must be deleted and recreated. All mandatory fields of a widget type MUST be completely filled in at creation; the system MUST reject incomplete configurations with HTTP 422.

**Implementation note (changes us-055-dashboards, S16, and us-056-kpi-and-chart-widgets, S17)**: all four types are accepted. A KPI or chart widget has the data source `property-aggregation` and the field `aggregation` ([REQ-053](./REQ-053-property-aggregation-datasource.md)); fields of another widget type are rejected with 422 naming the field, `unit` has at most 20 characters, a chart needs `groupBy` and `chartType` and has one series (hence no second axis), a KPI with the comparison `vs-target` needs `targetValue`, and `vs-previous-value` (change us-056-kpi-previous-value) needs nothing further and is rejected on a chart like every comparison. A table widget needs a catalog visible to its writer (403 otherwise) and, on a shared dashboard, a shared catalog (422 naming `catalogId`). Title: 1–150 characters, at most 50 widgets per dashboard, `pageSize` 5–100. The widget writes are `POST`, `PUT` and `DELETE` on `/dashboards/{id}/widgets[/{widgetId}]` and `PUT /dashboards/{id}/layout` for several positions at once; each returns the dashboard with its new version and needs `If-Match`.

## Rationale

Widgets are the business building blocks of a dashboard. The types are structurally different enough that they need separate validation rules, but they can be managed via a common API. Immutability of the type avoids migration complexity (e.g., DataSource format change).

## Context

Widget configuration takes place in the **client app**. The **web portal** renders widgets read-only. DataSource details (PropertyAggregation, CatalogQuery) are specified in REQ-053/REQ-054.

## Type-Specific Fields

### API Endpoints

**Add**:

- `POST /api/v1/dashboards/{dashboardId}/widgets`
- Request: widget configuration (type + type-specific fields + gridPosition)
- Response 200: the dashboard with the new widget and its new version (realigned 2026-11-18; formerly 201 with the widget)
- Response 422: validation error (missing mandatory fields, invalid DataSource)
- Response 403: the requester may not change the dashboard (a shared dashboard without `dashboard:manage`, another person's personal dashboard for an administrator); 404 for another person's personal dashboard
- Response 412 / 428: stale or missing `If-Match`

**Configure (update)**:

- `PUT /api/v1/dashboards/{dashboardId}/widgets/{widgetId}`
- widgetType must NOT be changed → 422 if different

**Remove**:

- `DELETE /api/v1/dashboards/{dashboardId}/widgets/{widgetId}`
- Response 204; no block if the dashboard thereby has 0 widgets (empty dashboard allowed)

### Widget Types and Mandatory Fields

| widgetType | Mandatory fields | Optional fields |
|---|---|---|
| `kpi` | title, dataSource (PropertyAggregation) | unit, comparisonMode, targetValue, numberFormat |
| `chart` | title, chartType, dataSource, yAxis | xAxis (not for pie/donut/treemap), colorScheme, showLegend, showDataLabels |
| `table` | title, dataSource (CatalogQuery) | pageSize, columnOverrides |
| `text` | title, content | – |

## Business Rules

| Rule-ID | Statement | Trigger |
|---|---|---|
| BR-01 | `widgetType` is immutable after creation | onUpdate |
| BR-02 | `chartType=pie` or `chartType=donut` may configure exactly one Y axis | onCreate, onUpdate |
| BR-03 | A `kpi` widget must have a `dataSource` of type `PropertyAggregation`; `CatalogQuery` is invalid | onCreate, onUpdate |
| BR-04 | A `table` widget must have a `dataSource` of type `CatalogQuery`; `PropertyAggregation` is invalid | onCreate, onUpdate |
| BR-05 | A `kpi` widget with `comparisonMode=vs-target` requires `targetValue` | onCreate, onUpdate |
| BR-06 | Every holder of `dashboard:manage` may add, change, or remove widgets of a shared dashboard; only the owner those of a personal dashboard (owner decision 2026-11-18) | onCreate, onUpdate, onDelete |

## Acceptance Criteria

**AC1** (Create KPI widget):

- When: `POST /api/v1/dashboards/{id}/widgets` with type=kpi, title="Total costs", dataSource={PropertyAggregation, entityType=plateau, propertyName=investmentCostForecast, aggregationFn=sum}, gridPosition={col:1,row:1,width:3,height:1}
- Then: HTTP 201; widget stored in DB; GET /api/v1/dashboards/{id} contains the widget

**AC2** (Chart widget, chartType=pie → no xAxis allowed):

- When: POST with type=chart, chartType=pie, two yAxis entries
- Then: HTTP 422 "pie/donut allows exactly one Y axis"

**AC3** (Type change rejected):

- Given: a widget type=kpi exists
- When: PUT with widgetType=chart
- Then: HTTP 422 "widgetType is immutable"

**AC4** (Invalid DataSource for table):

- When: POST with type=table, dataSource=PropertyAggregation
- Then: HTTP 422 "Table widget requires a CatalogQuery DataSource"

**AC5** (Remove):

- When: DELETE /api/v1/dashboards/{id}/widgets/{widgetId}
- Then: HTTP 204; the widget is no longer contained in GET /api/v1/dashboards/{id}

**AC6** (Someone else's dashboard):

- Given: Franz tries to add a widget to Kurt's dashboard
- When: POST without permission
- Then: HTTP 403

## Dependencies

- Blocked by: REQ-051 (the dashboard must exist)
- Takes DataSource validation from: REQ-053 (PropertyAggregation), REQ-054 (CatalogQuery)
- Enables: REQ-055 (data is computed from the widget configurations), REQ-057 (GridPosition)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; all 4 widget types, type immutability, BR-01–06 |
| 0.2.0 | 2026-11-18 | Fullstack Engineer | us-055-dashboards: who changes widgets; versions; the first implementation (text and table) |
| 0.3.0 | 2026-11-19 | Fullstack Engineer | us-056-kpi-and-chart-widgets: KPI and chart widgets accepted; fields per type |
