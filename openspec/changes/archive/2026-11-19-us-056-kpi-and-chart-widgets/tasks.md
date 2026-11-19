# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (D1): the new settings and data fields, the error codes, and the limits.

  Verify: Redocly lint, `python3 scripts/check_openapi_authorization.py` and its test pass.

## 2. Backend

- [x] 2.1 Validation at write (D2) in `Dashboards`.

  Verify with `DashboardWidgetsTests` (parameterized):
  - a KPI with count, with sum, avg, min and max of an integer property, and with count of all element entities;
  - sum of a string property gives 422 with the detail;
  - an unmapped property, an unknown metatype, and a null metatype with sum give 422 naming the field;
  - a KPI with `groupBy` gives 422; a chart without `groupBy` or `chartType` gives 422; a chart with `comparison` or `targetValue` gives 422;
  - a KPI or chart with `dataSource=catalog-query` or a `catalogId` gives 422 naming the field;
  - groupBy of a `date` property gives 422; `enum`, `string`, `boolean` and `reference` are accepted;
  - `vs-target` without `targetValue` gives 422, and `vs-previous-value` gives 422;
  - the unit at 20 is accepted and at 21 rejected.
- [x] 2.2 `core.entity.EntityAggregationStore` (D3) and `PropertyAccess.Snapshot.readableCompletely` (D4).

  Verify with store tests:
  - deleted entities are excluded, only the given metatypes are included, entities without a value of the aggregated property are left out;
  - the partials count, sum, min and max per group, without grouping, by metatype and by each property value column (text, bool, enum, ref with the referenced entity's name);
  - the key `null` for entities without a grouping value.

  Verify with `PropertyAccessTests`: `readableCompletely` is true for public and for role-restricted with a read role, and false without one, for connection-scoped with and without account link, and for an override role holder without a read role.
- [x] 2.3 `DashboardData` for KPI and chart, with visibility and errors (D4, D5).

  Verify with `DashboardDataTests`:
  - a role-restricted property gives `restricted` without the role and the value with it;
  - a connection-scoped property gives `restricted`;
  - a restricted grouping property gives `restricted`; an override role holder without a read role gets `restricted`; a restricted widget runs no store query;
  - count and the metatype or layer grouping are not restricted;
  - subtypes are included, `metatypeId=null` counts all element entities, and the layer grouping folds metatypes, with "(none)" for metatypes without a layer;
  - `value=null` for sum, avg, min and max over no values, and 0 for count;
  - 50 groups give 50 entries without "Other", and 51 give 50 plus "Other", with the folded value for count, sum, avg, min and max (parameterized);
  - order by value descending, then label; labels and `labelKey` per kind;
  - after unmapping the property or deleting the metatype, the widget gives `invalid-configuration` and the other widgets compute; after deleting a layer, its metatypes count under "(none)".
- [x] 2.4 Cucumber: extend `features/dashboard/widgets.feature` with the scenarios of the delta spec.

  Reuse the sessions of shared instances; never log the same IdP user in twice.

  Verify: the full Cucumber suite passes.

## 3. Frontend

- [x] 3.1 Stories first: SCR-024 with a KPI with a target, a KPI without data, bar, pie, donut, treemap, `restricted` and `invalid-configuration`, and the widget dialog for KPI and chart.

  Verify: the stories render without a11y violations, in light and dark.
- [x] 3.2 `KpiWidget.vue`, `ChartWidget.vue`, the extended `WidgetDialog.vue`, `api/dashboards.ts` and the fake (D6). Texts go in `i18n` en/de.

  Verify with specs:
  - the number format and the unit;
  - the trend for above, below and equal;
  - "– (no data)";
  - the chart options per chart type (bar, line, area, pie, donut, treemap), with legend and data labels on and off;
  - the "(none)", "Other", "Yes" and "No" labels localized, and labels through `labelKey` with the name as fallback;
  - the dialog offers only integer properties for sum, avg, min and max, and only enum, string, boolean and reference properties for grouping;
  - labels with markup are shown literally, in the tooltip as well.
- [x] 3.3 ECharts and the licenses (D6): add `echarts`; extend `frontend/third-party-licenses.ts` to ship `NOTICE` files; regenerate `THIRD-PARTY-LICENSES.md` with `python3 scripts/generate_third_party_licenses.py`.

  Verify:
  - `npm run build`, and `dist/third-party-licenses.txt` contains the ECharts `NOTICE`;
  - `python3 scripts/generate_third_party_licenses.py --check` lists `echarts`, `zrender` and `tslib` with allowed licenses.

## 4. Documentation and wrap-up

- [x] 4.1 The documentation of D7.
- [x] 4.2 Regenerate `requirements/traceability.md`.
- [x] 4.3 Run the checks and verify they pass:
  - `python3 scripts/validate_links.py`;
  - markdownlint;
  - `openspec validate us-056-kpi-and-chart-widgets --strict`;
  - `python3 scripts/check_manual.py`;
  - `python3 scripts/check_screens.py`.
