# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (D1): `vs-previous-value` accepted; `previousValue` and `previousAt` in `WidgetData`.

  Verify: Redocly lint, `python3 scripts/check_openapi_authorization.py` and its test pass.

## 2. Backend

- [x] 2.1 `EntityAggregationStore.aggregateAt` (D2), with store tests and a fixed clock:
  - an entity created exactly at `at` is counted, one created one second after `at` is not;
  - an entity deleted exactly at `at` is not counted, one deleted one second after `at` is counted;
  - the value at `at`: unchanged since creation (no history row, current value), changed after `at` (old value of the first later row, also with two later changes), changed exactly at `at` (the new value), removed after `at` (counts), set for the first time after `at` (does not count with a property);
  - subtypes are included, other metatypes are not;
  - no entities gives count 0, and sum gives null; empty metatypes run no query.
- [x] 2.2 `DashboardData` and the validation (D3).

  Verify with `DashboardAggregationTests` (parameterized over count, sum, avg, min and max):
  - `previousValue`, and `previousAt` exactly 30 × 24 h before the fixed clock;
  - the boundary scenario of the delta spec (created exactly at `previousAt`, and one second later);
  - a restricted property gives `restricted` and runs no query;
  - `invalid-configuration` as before;
  - `vs-previous-value` on a chart gives 422.
- [x] 2.3 Cucumber: extend `features/dashboard/widgets.feature` with the scenarios of the delta spec. The past state is written with times relative to now (entities created and deleted days ago, history rows of changes days ago), so the clock of the shared instance is not advanced and no own instance is needed.

  The scenario "Boundary of the period" needs exact instants, which the `OffsetClock` (system time plus offset) cannot give: it is covered by the store tests of 2.1 and by `DashboardAggregationTests` with a fixed clock, and the feature file refers to them.

  Verify: the full Cucumber suite passes.

## 3. Frontend

- [x] 3.1 Stories: SCR-024 with KPIs increased, decreased, equal and without a comparison value.

  Verify: the stories render without a11y violations.
- [x] 3.2 `KpiWidget.vue` and `WidgetDialog.vue` (D5), with `i18n` in en and de.

  Verify with specs:
  - the difference with its sign, and the percentage hidden when the previous value is 0;
  - the arrows for up, down and equal;
  - "No comparison value";
  - the mode can be selected in the dialog.

## 4. Documentation and wrap-up

- [x] 4.1 The documentation of D6, including the implementation note of REQ-052.
- [x] 4.2 Run the checks and verify they pass:
  - `python3 scripts/validate_links.py`;
  - markdownlint;
  - `openspec validate us-056-kpi-previous-value --strict`;
  - `python3 scripts/check_manual.py`;
  - `python3 scripts/generate_traceability.py --check`.
