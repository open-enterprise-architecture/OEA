# Design

## Context

- **Query:** `CatalogQueries.query` resolves the columns (primary, endpoint and join columns, us-047, us-048, us-080), the sort and the join modes, then `CatalogQueryStore` runs one statement each for count and page, and one aggregate statement per aggregate join.
- **Column values:** they come from the system fields of the entity (`SystemField`) or from `entity_property_values` with typed columns (`value_text`, `value_enum`, `value_int`, `value_date`, `value_bool`, `value_ref`, ADR-022).
- **Configuration:** saved filters live in `catalogs.config.savedFilters`, which exists empty since us-046. The contract has placeholder schemas `CatalogSavedFilter` and `CatalogFilterExpression`.
- **URL state:** the page keeps `sort`, `page` and `mode` in the URL (ADR-044); `viewOf` parses and validates them.

## Goals / Non-Goals

**Goals:**

- ad-hoc filters on every visible column, with the 12 operators, AND/OR and the join semantics of the owner decision;
- saved filters with add, remove and quick selection;
- the URL state.

**Non-Goals:**

- saved views (change 4);
- editing saved filters in place;
- filters on hidden attributes;
- performance tuning (change 7).

## Decisions

### D1 Contract (delta to `api/openapi.yaml`)

- **`queryCatalog`:**
  - `filter`: `in: query`, `style: form`, `explode: true`, an array of strings `<key>:<operator>[:<value>]`. The key is a column key of the result: `name`, a property's `sqlColumnName`, `source.<key>`/`target.<key>` or `<joinId>.<key>`. The value is everything after the second colon. For `in`/`notIn` it is a comma-separated list, and it is absent for `isNull`/`isNotNull`.
  - `filterMode`: enum `and`, `or`, default `and`.
  - `CatalogQueryProblem.invalidFields` gains `filter`.
- **`addCatalogFilter`:**
  - `POST /catalogs/{id}/filters` with `CatalogSavedFilterInput`: `name` (1–100), `description` (≤ 1000, nullable), `logicalOperator`, and `expressions` (1–20 `CatalogFilterExpression`);
  - 201 with the catalog; 403, 404 and 422 (`CatalogProblem`);
  - the condition for instance catalogs is enforced by the handler and probed as for the joins.
- **`removeCatalogFilter`:** `DELETE /catalogs/{id}/filters/{filterId}`, 200 with the catalog; 403 and 404.
- **`CatalogFilterExpression`:**
  - `attributePath` is a column key as above;
  - `value` is a string, or an array of strings for `in`/`notIn`, or absent;
  - `CatalogSavedFilter` gains `available`, as for joins: `false` if an expression names a column the catalog no longer shows.
- **`Catalog.savedFilters`:** loses the "follow later" note.

### D2 Filter model and validation

- **`CatalogQueries` parses each `filter` into `Filter(key, operator, values)`** against the resolved columns of the query.
- **The key** must be a visible primary column or a column of an available join (any mode); otherwise 400 `filter`.
- **The column type** decides the operators:
  - a system field: text for `name`, `description` and `externalId`; date for `createdAt` and `updatedAt`; number for `entityVersion`;
  - a property: its `dataType` (`string`, `text` and `url` are text, `integer` is number, `date` is date, `enum` is enum, `boolean` is boolean, `reference` is number by entity ID).

  | Type | Operators |
  |---|---|
  | text | `eq`, `neq`, `contains`, `startsWith`, `in`, `notIn`, `isNull`, `isNotNull` |
  | enum | `eq`, `neq`, `in`, `notIn`, `isNull`, `isNotNull` |
  | boolean | `eq`, `neq`, `isNull`, `isNotNull` |
  | number, date | `eq`, `neq`, `gt`, `lt`, `gte`, `lte`, `isNull`, `isNotNull` |
- **Values:**
  - a number or date must parse (`yyyy-MM-dd`; for `createdAt`/`updatedAt`, the date compares with the UTC day);
  - an enum value must be one of the property's enum values;
  - a boolean is `true` or `false`;
  - every operator except `isNull` and `isNotNull` needs a value.

  Anything else gives 400 `filter`.
- **The same check validates saved-filter expressions** against the catalog's current columns, with the query's default columns and every available join. A failure gives 422 `expressions[i].attributePath`, `.operator` or `.value`. At most 20 filters apply per query.

### D3 Store

`CatalogQueryStore` gains `Condition(column, operator, values)` and takes `primaryConditions`, `joinConditions` per join, and `or`.

- **Primary condition:** a predicate on the value expression of the column, at the same aliases as the column (`e`, `es`, `et`, `v<i>` property joins).
  - Text: `lower(<text>) = lower(?)`, `LIKE lower(?) || '%'` (`startsWith`), `LIKE '%' || lower(?) || '%'` (`contains`), with the wildcards in the value escaped.
  - Number, date and enum: the typed columns (`value_int`, `value_date`, `value_enum`) or the system field's sort expression.
  - `isNull` and `isNotNull` test the text form.
  - Property values are joined for filtered columns even if they are not shown.
- **Join condition:** `EXISTS (SELECT 1 FROM (<pairs>) p JOIN entities t … <property joins> WHERE p.pid = e.id AND <join conditions>)` restricts the entries.
  - The same conditions restrict the results: in `aggregate`, the statement adds them to its `WHERE`; in `rows`, they go into the lateral subquery, which then holds only matching targets.
  - With AND, an entry without a matching result is dropped. A rows join with a condition then never shows an empty join row.
- **OR:** the predicates combine as `(p1 OR p2 OR EXISTS(...))`. The join results of a join are narrowed by the OR of its own conditions. In rows mode the lateral join becomes a `LEFT JOIN`, so an entry that passes through a primary condition keeps one row.
- **`count`** applies the same conditions.
- **Arguments:** all values are bound parameters of `Statement`.

### D4 Saved filters

- **Storage:** `Catalogs.addFilter` and `removeFilter` store `config.savedFilters[]`: `{id, name, description, logicalOperator, expressions[{attributePath, operator, value}]}`.
- **Permissions:** `writable()`, the rule of the columns.
- **Audit:**
  - `catalog.filter-added`, with name, operator and the number of expressions;
  - `catalog.filter-removed`, with the name.
- **Limits:** 50 filters, each with 1–20 expressions; the name is unique case-insensitively after trimming.
- **Joins:** `removeJoin` drops the expressions whose key starts with the join ID, and the filters left empty (BR-04).
- **Reading:** `available` is computed from the current columns, as for columns and joins.

### D5 SCR-020

- **`CatalogFilterBar.vue`** above the table:
  - one chip per filter, labelled "<column> <operator> <value>" with translated labels and enum labels, plus an X to remove it;
  - "Add filter" opens a popover with three steps: the column (primary, then each join group), the operator (by type, D2), and the value. The value input is a text field, a number field, a date field or a multi-select of enum values for `in`/`notIn`.
  - an AND/OR switch, shown from two filters on;
  - "Save filter", shown only with the right to configure (`writable`), opens a modal with name and description;
  - the quick selection, a `USelect` of the saved filters, shown when the catalog has any. An unavailable filter is shown disabled with "Not available".
- **URL:**
  - `filter` repeats per chip and `filterMode=or` is set only for OR; changes go through `router.replace` and reset the page to 0;
  - `viewOf` accepts at most 20 `filter` values of the form `<key>:<op>[:<value>]` and leaves the column check to the server;
  - on a 400 `filter`, the page drops the filters and shows the default view, as for an invalid sort.
- **Activating a saved filter** writes its expressions and operator into the URL. Expressions on columns the result no longer has are left out, with a hint.

### D6 Data model and documentation

- **`business-objects/catalog.md`:**
  - SavedFilter as implemented, without `scope`, with the limits and `available`;
  - FilterExpression with column keys;
  - BR-04 sharpened: an expression names a column of the catalog, and removing a join removes its expressions;
  - a new BR-12 for the operators per type.
- **`docs/data-model.puml`:** `config.savedFilters` typed.
- **`docs/audit-event-catalog.md`:** the two events.

No migration.

## Risks / Trade-offs

- **Correlated `EXISTS` per join filter, with `OR` across them:** with OR the planner cannot use the index on the primary metatype as selectively. This is acceptable for S9; the measurements follow in change 7 (#66).
- **Commas inside values** cannot be used with `in`/`notIn`. Use `eq` for such values instead.
- **Filters only on visible columns:** to filter on a hidden attribute, the catalog manager has to show it as a column. This keeps the bar, the URL and the saved filters tied to what the reader sees.
