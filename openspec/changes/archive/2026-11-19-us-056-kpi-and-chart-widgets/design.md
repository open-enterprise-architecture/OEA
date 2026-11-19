# Design

## Context

- **Dashboards (S16, `us-055-dashboards`):**
  - `dashboard_widgets.config` is JSONB.
  - `Dashboards` validates widget configs and `DashboardData` computes `WidgetData` per widget in its own try block.
  - The contract has `WidgetSettings` as one flat object and `WidgetData` with `status` and `error`.
  - The frontend has `components/dashboard/{WidgetDialog,DashboardGrid,TextWidget,TableWidget}.vue`.
- **Metamodel:**
  - property data types are `string`, `integer`, `boolean`, `enum`, `reference`, `date`, `url` and `text`;
  - metatypes have base types (subtypes);
  - layers came with `s17-architecture-layers` (`metatypes.architecture_layer_id`).
- **Property visibility:** `PropertyAccess` and `PropertyAccess.Snapshot` in `app.metamodel` decide readability per reader (`readableFor` and `readableWithoutEntity`), with the modes public, role-restricted and connection-scoped.
- **ADR-051:** SQL lives only in `core` stores, and the freeze has ended. A new JDBC use in `app` fails the build.

## Goals / Non-Goals

**Goals:** as in the delta spec `dashboard/widgets`.

**Non-goals:**

- `vs-previous-value`;
- the portal;
- filters in the data source;
- domains.

## Decisions

### D1 – Contract

**`WidgetSettings` gains:**

- `aggregation`: `{metatypeId: uuid|null, propertyDefId: uuid|null, function: count|sum|avg|min|max, groupBy: {kind: property|metatype|layer, propertyDefId?: uuid} | null}`;
- `unit` (string, ≤ 20);
- `comparison`: `none|vs-target|vs-previous-value`. `vs-previous-value` is rejected with 422 until change 6;
- `targetValue` (number);
- `chartType`: `bar|line|area|pie|donut|treemap`;
- `showLegend` (boolean, default true) and `showDataLabels` (boolean, default false).

`dataSource` stays (`property-aggregation` for kpi and chart, `catalog-query` for table).

**`WidgetData` gains:**

- `value` (number|null) and `target` (number|null);
- `series`: `[{kind: value|none|other|true|false, key: string|null, label: string|null, labelKey: string|null, value: number}]`, at most 51 entries (50 groups plus "Other"):
  - `kind=value`: `key` is the enum value, the string value, the referenced entity's ID, the metatype ID or the layer ID; `label` is the name in the corporate language (enum value, string, referenced entity's name, metatype name, layer name);
  - `labelKey` is the key of the label bundle (`enum-value.{id}.label`, `metatype.{id}.name`, `architecture-layer.{id}.name`), null for strings and references; the client shows `label(labelKey, label)` as everywhere else (ADR-025, `i18n/labels.ts`);
  - `kind=none`, `other`, `true` and `false` have no `key` and `label` and are localized by the client ("(none)", "Other", "Yes", "No");
- `value` semantics: `count` over no entities gives 0; `sum`, `avg`, `min` and `max` over no values give `value=null` ("– (no data)", US-056 AC4);
- the error codes `restricted` and `invalid-configuration`.

### D2 – Validation at write (`app.dashboard.Dashboards`)

**KPI:**

- `aggregation` is required;
- `groupBy` must be null (422 naming `groupBy`);
- `comparison` defaults to `none`, and `vs-target` needs `targetValue`;
- `chartType`, `showLegend` and `showDataLabels` are rejected when set (422).

**KPI and chart:** `dataSource` is `property-aggregation` (the default for these types); `catalog-query`, `catalogId`, `savedViewId`, `pageSize` and `content` give 422 naming the field (US-056 technical note).

**Chart:**

- `aggregation` and `groupBy` are required;
- `chartType` is required;
- `unit` is allowed (shown in tooltips and data labels); `comparison` and `targetValue` give 422 naming the field.

**Aggregation:**

- **`metatypeId`:** an existing element metatype of the instance metamodel (422 naming `metatypeId`). Null is allowed only with `function=count` and without a property.
- **`propertyDefId`:** a property mapped (effective now) to the metatype or one of its base types (422 naming `propertyDefId`). `sum`, `avg`, `min` and `max` need a property of type `integer` (422 with the detail "sum requires an integer property", naming `function`).
- **`groupBy.kind=property`:** a mapped property of type `enum`, `string`, `boolean` or `reference` (422 naming `groupBy`). The kinds `metatype` and `layer` are always valid.
- **Entity name:** grouping by the entity name (US-057 AC1, `groupBy=name`) is not offered: the owner decision of 2026-11-18 names property, metatype and layer, and the name is a system field, not a property. "Investment per plateau" is a grouping by an enum or reference property; US-057 AC1 is realigned accordingly (D7).

### D3 – Aggregation query (`core.entity.EntityAggregationStore`)

`entities` and `entity_property_values` belong to `core.entity` (ADR-051: one owner per table, other features use its store), so the store lives there, not in `core.dashboard`.

**The method:**

```text
aggregate(Set<UUID> metatypeIds, UUID valuePropertyDefId|null,
          Grouping grouping /* NONE, METATYPE, or PROPERTY with propertyDefId and ValueColumn */)
  -> List<GroupRow(String key, String name, long count, Long sum, Long min, Long max)>
```

- It is built from typed arguments only: the value column is an enum (`TEXT`, `BOOL`, `REF`, `ENUM`) mapped to fixed SQL snippets inside the store; IDs are bind parameters. No SQL fragment comes from `app`.
- **Scope:** `entities` with `deleted_at IS NULL` and `metatype_id = ANY(?)`; only the catalog, no deltas of solutions.
- **Values:** with a value property, only entities with a `value_int` of it are aggregated (`count` then counts the entities with a value); without one, every entity counts.
- **Partials:** each group row carries `count`, `sum`, `min` and `max` of `value_int`, so every function, including `avg = sum / count`, and the rest group can be derived without a second query.
- **Grouping by property:** the key is `value_text`, `value_bool`, `value_enum`, or `value_ref` with the referenced entity's `name` as name; entities without a value of the grouping property form the key `null`.
- **Grouping by metatype:** the key is `metatype_id`.

**In `app.dashboard.DashboardData`:**

- **Metatypes:** the metatype and its subtypes from `MetatypeStore.bases()` (as `MetamodelEffects.subtree`), or all element metatypes of the instance metamodel for `metatypeId=null`.
- **Layer grouping:** the store groups by metatype, and `app` folds the partials per layer through the metatypes' `architecture_layer_id` (metatypes without a layer form "(none)"). Layers are at most 20.
- **Labels:** metatype and layer names come from the metamodel stores, enum value IDs from the enum values of the property (for `labelKey`); the client localizes (D1). The server does not resolve labels per reader language.
- **Order and limit:** groups are ordered by value descending, then by `label`; "(none)" is ranked like any group. The first 50 are shown; the rest is folded from the partials into "Other" with the same function (sum of counts or sums, min of mins, max of maxes, total sum / total count for `avg`).

### D4 – Visibility (`app.dashboard.DashboardData`)

- **Property check:** before querying, the aggregated property and the grouping property are checked with the reader's `PropertyAccess.Snapshot`:
  - **public:** readable;
  - **role-restricted:** readable only if the reader holds a read role;
  - **connection-scoped:** not completely readable, so the widget gets the error `restricted`, even for a reader with an account link. A per-entity filter would make aggregates misleading, and a per-entity check would cost one scope query per entity.
- **New method:** `Snapshot.readableCompletely(propertyDefId)`, which is `allowed(propertyDefId, false)`: public, or role-restricted with a read role. The existing `readable` and `readableWithoutEntity` do not fit: both include the override, and `readable` is true for a connection-scoped property with an account link.
- **No property or metatype/layer grouping:** with no property, or with grouping by metatype or layer, there is no property check.
- **Override roles:** they do not lift `restricted` for aggregates. Override reads are audited per value (REQ-128); an aggregate is no audited read of values.
- **Order:** the check runs before the store is called, so a restricted widget runs no query.

### D5 – Errors at read

On read, `metatypeId`, `propertyDefId` and the grouping property are re-validated against the current metamodel:

- metatype unknown or no longer an element metatype;
- property no longer mapped, or of the wrong type;
- grouping property no longer mapped.

Any of these gives `invalid-configuration` for the widget only, as REQ-055 requires.

### D6 – Frontend

- **`KpiWidget.vue`:**
  - the value formatted with `Intl.NumberFormat` in the UI locale, plus the unit;
  - with `vs-target`, the target, the difference and an arrow up, down or equal (icon plus text, not color alone);
  - `value=null` shows "– (no data)".
- **`ChartWidget.vue`:**
  - ECharts through `echarts/core` with only the needed charts (bar, line, pie, treemap) and components (tooltip, legend, grid);
  - `area` is a line with `areaStyle`, and `donut` is a pie with an inner radius;
  - tooltips show the label and the formatted value; a custom tooltip formatter escapes the label with `echarts.format.encodeHTML` (the HTML tooltip sets `innerHTML`);
  - the theme follows light or dark;
  - a hidden table with the series is provided for screen readers.
- **`WidgetDialog.vue`:**
  - for `kpi` and `chart`: the metatype (or "All element types" for count), the function, the property filtered by type, and the grouping (property, metatype, layer);
  - for `kpi`: the unit, the comparison and the target;
  - for `chart`: the chart type, the legend and the data labels.
- **Fakes and stories:**
  - `src/test/dashboards.ts` serves the new data;
  - SCR-024 stories: a KPI with target, a KPI with no data, bar, pie, treemap, `restricted`, `invalid-configuration`.
- **Licenses:** `echarts` (Apache-2.0), its dependencies `zrender` (BSD-3-Clause) and `tslib` (0BSD), all allowed by ADR-043. ECharts ships a `NOTICE` file, which Apache-2.0 §4(d) requires to be passed on: the Vite plugin `frontend/third-party-licenses.ts` adds `NOTICE` files to `third-party-licenses.txt` (today it reads only `LICENSE`/`COPYING`). `THIRD-PARTY-LICENSES.md` is regenerated.

### D7 – Documentation

- **Manual:** "Dashboards" gets the KPI and chart widgets, in English and German.
- **Business object:** `business-objects/dashboard.md` gets the data source as implemented (IDs instead of names, groupBy kinds).
- **Requirements:** REQ-053 is realigned (IDs, groupBy kinds, `restricted`, the 50-group limit, no domains, no filters); US-057 AC1 is realigned to grouping by a property (D2) and AC2 notes that domains follow; US-057 is set to done and US-056 stays in progress until change 6.
- **Sprint plan:** the S17 status.

### D8 – Security

- **Authorization:** the same as for dashboards; the aggregates run with the reader's visibility (D4).
- **No leakage:** a role-restricted or connection-scoped property never contributes to an aggregate shown to a reader who cannot read its values (both readers tested).
- **SQL:** built only from enums and parameters inside the store.
- **Input limits, both sides tested:**
  - the unit at 20 and 21;
  - the title, as in S16;
  - `targetValue`: any finite number; `NaN` and Infinity are rejected by JSON;
  - the groups: 50 shown, and the 51st becomes "Other" (tested with 50 and 51 distinct values);
  - the chart type, function and kind: enum values, where an unknown one gives 400.
- **Output encoding:** labels are rendered as text by ECharts (no `rich` HTML), escaped in the HTML tooltip (D6), and as text in the hidden table.
- **No leakage, override:** a holder of an override role without a read role gets `restricted` (tested).

## Risks / Trade-offs

- **Aggregation cost:** for large repositories it is bounded by the indexes on `entities.metatype_id` and `entity_property_values`. No caching (REQ-055: live data). A string grouping returns one row per distinct value to `app`, which is bounded by the number of entities.
- **Connection-scoped properties:** they cannot be aggregated for readers without full access. This is documented as a limitation.

## Migration Plan

None, because widget configs are JSON.

## Open Questions

None.
