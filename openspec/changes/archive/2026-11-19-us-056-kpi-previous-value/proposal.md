# Proposal

## Why

The KPI widget of US-056 offers three comparison modes. `none` and `vs-target` came with change 5 (`us-056-kpi-and-chart-widgets`). The third, `vs-previous-value`, needs a past value of the same aggregation. The owner decided on 2026-11-18 that the previous value is the value the aggregation had 30 days ago, computed from the entity history.

This is S17 change 6 ([sprint plan](../../../docs/sprint-plan.md)). It completes US-056.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-07](../../../requirements/use-cases/UC-07-create-and-use-dashboard.md) |
| User Story | [US-056](../../../requirements/user-stories/US-056-configure-kpi-widget.md) (comparison modes) |
| Requirements | [REQ-053](../../../requirements/req/REQ-053-property-aggregation-datasource.md), [REQ-055](../../../requirements/req/REQ-055-retrieve-dashboard-data.md) |
| ADRs | [ADR-022](../../../adrs/ADR-022-structured-property-model.md) (versions as deltas), [ADR-032](../../../adrs/ADR-032-authorization-model.md), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decision of 2026-11-18:** `vs-previous-value` compares with the value the same aggregation had 30 days ago, computed from the entity history.

**Decisions and assumptions of this change (open for review):**

- **Point in time:** the current time minus 30 days, measured on the server (`Clock`). The period is fixed and not configurable.
- **Entities counted at that time:** the element entities of the metatype and its subtypes that existed then. That means created at or before that time, and not deleted at or before that time.
  - An entity deleted later counts.
  - An entity created later does not count.
  - A deletion is final (a restore restores versions, not deleted entities), so `deleted_at` is exact.
- **Values at that time:** for each entity, the property value at that time: the old value of its first history row after that time, or the current value if it has not changed since.
- **Metamodel:** the current one (mapping, subtypes, data types). The metamodel is not reconstructed for the past, so a metatype that changed its base type since then is aggregated by its current base type.
- **Visibility:** the same check as the current value (`readableCompletely`). A restricted property shows neither the current nor the previous value.
- **Display:**
  - the current value, the previous value with the label "30 days ago", the difference and its percentage (the percentage only when the previous value is not 0);
  - an arrow up, down or equal, shown as icon and text;
  - without a previous value (no data 30 days ago), "No comparison value".
- **Chart widgets:** they have no comparison (as in change 5).
- **Cost:** one additional aggregation query per KPI with this mode, served by the existing history index; no migration.

**Contract:**

- `comparison=vs-previous-value` is accepted.
- `WidgetData` gains `previousValue` (number or null) and `previousAt` (date-time).

**Not in this change:**

- a configurable period;
- KPI snapshots;
- a reconstruction of the metamodel for the past;
- comparisons for charts.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `dashboard/widgets`: the KPI widget's comparison with the value 30 days ago.

## Impact

- **Contract:** `WidgetData` and the description of `comparison`.
- **Backend:**
  - a historical aggregation in `core.entity.EntityAggregationStore` (typed arguments, a timestamp);
  - the validation and the computation in `app.dashboard`;
  - no migration.
- **Frontend:** the KPI widget, the widget dialog (the mode becomes selectable), stories and fakes.
- **Documentation:** the manual "Dashboards" (en/de), REQ-052, REQ-053 and US-056 (done).
