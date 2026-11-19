# Proposal

## Why

The dashboard scaffold of S16 has text and table widgets. The CIO view of UC-07 needs figures and distributions: a KPI widget shows one aggregated value (US-056), and a chart widget shows an aggregation grouped into bars, lines, areas, pie or donut segments, or a treemap (US-057).

Both widgets compute from a property aggregation data source over the catalog (REQ-053). Change 1 of S17 (`s17-architecture-layers`) added the layers that charts group by.

This is S17 change 5 ([sprint plan](../../../docs/sprint-plan.md)). Change 6 (`us-056-kpi-previous-value`) adds the comparison with the value 30 days ago. Change 7 (`us-061-portal-dashboards`) shows dashboards in the portal.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-07](../../../requirements/use-cases/UC-07-create-and-use-dashboard.md) |
| User Story | [US-056](../../../requirements/user-stories/US-056-configure-kpi-widget.md) (AC1–AC5; the mode `vs-previous-value` follows in change 6), [US-057](../../../requirements/user-stories/US-057-configure-chart-widget.md) (AC1–AC6; domains follow when they exist) |
| Requirements | [REQ-052](../../../requirements/req/REQ-052-configure-widget.md), [REQ-053](../../../requirements/req/REQ-053-property-aggregation-datasource.md), [REQ-055](../../../requirements/req/REQ-055-retrieve-dashboard-data.md) |
| ADRs | [ADR-032](../../../adrs/ADR-032-authorization-model.md) (authorization, property visibility), [ADR-043](../../../adrs/ADR-043-third-party-license-policy.md) (chart library), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-18:**

- **Grouping:** charts group by a property of the metatype, by the metatype itself, or by the architecture layer.
- **Comparison:** the KPI compares with a target value (`vs-target`). The comparison with the value 30 days ago follows in change 6.

**Decisions and assumptions of this change (open for review):**

- **Data source `property-aggregation`:**
  - **`metatypeId`:** an element metatype. Its subtypes are included. Null is allowed only for `count`, and then counts all element entities (e.g. "entities per type").
  - **`propertyDefId`:** a property mapped to the metatype. Null means the entities themselves, which is only allowed with `count`.
  - **`aggregation`:** `count`, `sum`, `avg`, `min` or `max`. The last four need an `integer` property (422 "sum requires an integer property").
  - **`groupBy`:** for charts only. It is one of a property of the metatype of type `enum`, `string`, `boolean` or `reference` (grouped by the referenced entity's name), the metatype, or the layer.
  - **Scope:** only entities that are not deleted are counted, in the catalog; deltas of solutions are not counted.
- **KPI widget:**
  - the aggregated value, the `unit` (at most 20 characters) and `comparison` `none` or `vs-target`;
  - `vs-target` needs `targetValue` (422 otherwise, US-056 AC5);
  - `vs-previous-value` gives 422 until change 6;
  - no value gives the display "– (no data)" without an error (AC4).
- **Chart widget:**
  - `chartType` is `bar`, `line`, `area`, `pie`, `donut` or `treemap`, with one series per chart (so AC3 "two y axes" cannot occur);
  - `groupBy` is required; `unit` is allowed, `comparison` and `targetValue` are rejected;
  - `showLegend` (default true) and `showDataLabels` (default false);
  - at most 50 groups, by value descending; the rest is summed as "Other";
  - entities without a value form the group "(none)";
  - labels are names, never IDs (AC2, AC4).
- **Visibility:** a property whose values the reader may not read for every entity (role-restricted without a read role, or connection-scoped) gives the widget the error `restricted`; override roles do not lift it. Aggregates never leak values the reader cannot see. `count` without a property and grouping by metatype or layer are always readable.
- **Metamodel changes:** a metatype or property that no longer exists or no longer fits gives the widget the error `invalid-configuration` on read (REQ-053 BR-05). A deleted layer moves its metatypes to "(none)". Widgets are never deleted automatically.
- **Entity name:** grouping by the entity name (US-057 AC1) is not offered; AC1 is realigned to grouping by a property.
- **Labels:** the data carries names and label keys; the client localizes them with the label bundle, as elsewhere.
- **Chart library:** Apache ECharts (Apache-2.0, with `zrender` BSD-3-Clause and `tslib` 0BSD, all permitted by ADR-043), imported modularly; its `NOTICE` is shipped with the license texts.

**Contract (spec-first):**

- `WidgetSettings` gains `aggregation` (the data source), `unit`, `comparison`, `targetValue`, `chartType`, `showLegend` and `showDataLabels`.
- `WidgetData` gains `value`, `target` and `series` (`[{kind, key, label, labelKey, value}]`) and the error codes `restricted` and `invalid-configuration`.
- `kpi` and `chart` are no longer rejected.

**Not in this change:**

- `vs-previous-value` (change 6);
- the portal (change 7);
- filters in the data source (REQ-053, optional);
- architecture domains;
- number formats other than the locale default.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `dashboard/widgets`: KPI and chart widgets, the property aggregation, its visibility and its errors; the requirement "Widgets of a dashboard" is replaced by "Widgets and their types".

## Impact

- **Contract:** the widget schemas.
- **Backend:**
  - the aggregation query in `core.entity.EntityAggregationStore` (the owner of the entity tables, ADR-051), built from typed arguments without SQL text from `app`;
  - `PropertyAccess.Snapshot.readableCompletely`;
  - the validation and the data computation in `app.dashboard`.
- **Migration:** none, because widget configs are JSON.
- **Frontend:** the KPI and chart widgets, the widget dialog with the data source, ECharts, fakes and stories; the license plugin ships `NOTICE` files. `THIRD-PARTY-LICENSES.md` is regenerated.
- **Documentation:**
  - the manual "Dashboards" (en/de);
  - `business-objects/dashboard.md`;
  - REQ-053 and the user stories US-056 and US-057.
