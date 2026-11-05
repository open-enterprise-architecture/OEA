# Proposal

## Why

A catalog shows every entity of its primary metatype. Once a repository holds hundreds of entities, readers need to narrow the list to what matters for their task: "active applications only", "interfaces using REST", "data flows carrying personal data". UC-06 A1 and A2 describe this as live filters that a reader sets and that a catalog keeps as named saved filters for quick selection (US-051, US-052, REQ-047).

The filter on the source and target of a connection catalog (US-080 AC5) and the GDPR example of a data flow catalog filtered by the classification of the carried data objects (US-080 AC6, REQ-064) were moved into this change (owner decision 2026-11-05).

This is S9 change 3 of the cut of 2026-11-05 (see `us-048-catalog-joins`).

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-06: Create, Configure and Use an Architecture Catalog](../../../requirements/use-cases/UC-06-create-and-use-catalog.md) (A1, A2, main flow step 13) |
| User Stories | [US-051: Set an Ad-Hoc Filter](../../../requirements/user-stories/US-051-ad-hoc-filter.md), [US-052: Save a Set Filter](../../../requirements/user-stories/US-052-saved-filter.md), [US-080](../../../requirements/user-stories/US-080-catalog-join-connection-type-primary.md) AC5, AC6 |
| Requirements | [REQ-047: Set Filters and Save as SavedFilter](../../../requirements/req/REQ-047-set-and-save-filters.md), [REQ-046](../../../requirements/req/REQ-046-catalog-query.md) AC3 (filters in the query), [REQ-064](../../../requirements/req/REQ-064-gdpr-catalog-filter.md) (GDPR example) |
| ADRs | [ADR-044: View State in the URL](../../../adrs/ADR-044-view-state-in-url.md), [ADR-032: Authorization Model](../../../adrs/ADR-032-authorization-model.md) (§8), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md), [ADR-045: User Manual](../../../adrs/ADR-045-user-manual.md) |
| Screen | SCR-020 Catalog Browser |
| Sprint / Milestone | S9 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

Owner decisions of 2026-11-05:

- **One change** for US-051 and US-052, with all 12 operators of REQ-047: `eq`, `neq`, `contains`, `startsWith`, `in`, `notIn`, `isNull`, `isNotNull`, `gt`, `lt`, `gte`, `lte`.
- **A filter on a join column restricts both:**
  - an entry appears only if it has at least one join result that matches;
  - the join column shows only the matching results.

  This follows US-051 AC2 and replaces the "join results only" reading of REQ-047.
- **Ad-hoc filters are part of the URL view state** (ADR-044 rule 1), like sort and join mode. They survive back navigation and reload and can be shared as a link.
- **Saved filters belong to the catalog configuration.** They are changed under the same rule as the columns: `catalog:manage` for an instance catalog, only the creator for a personal catalog. Every reader of the catalog sees them in the quick selection. A reader without that right saves filters in a personal catalog of their own. The `scope` of a saved filter (US-052 AC3, REQ-047 AC6) is dropped.

Further scope:

- **Query:** `queryCatalog` gains:
  - the repeated parameter `filter=<column key>:<operator>[:<value>]`, for example `filter=status:eq:active`, `filter=source.name:contains:CRM` or `filter=<joinId>.protocol:in:REST,SOAP`;
  - `filterMode=and|or`, default `and`.

  Filters apply to the columns of the result, including endpoint and join columns, and are combined before paging. An invalid filter answers 400 `filter`.
- **Saved filters:** `addCatalogFilter` (`POST /catalogs/{id}/filters`) and `removeCatalogFilter` (`DELETE /catalogs/{id}/filters/{filterId}`), with:
  - name (1–100 characters, unique in the catalog regardless of case);
  - optional description;
  - `logicalOperator` (`and`/`or`);
  - 1–20 expressions.

  Each change is audited.
- **SCR-020:**
  - a filter bar above the table with one chip per filter;
  - "Add filter": column → operator (by data type) → value, with the enum values offered for an enum column;
  - removing a chip;
  - an AND/OR switch;
  - "Save filter" (name and description);
  - a quick selection of the saved filters, which loads a filter's expressions and operator into the bar;
  - the filter bar is part of the URL.
- **Manual:** "Filters" in "Catalogs", in English and German.

Realigned sections, which were non-binding until this change:

- **REQ-047:**
  - `attributePath` names a column key of the result (`status`, `source.name`, `<joinId>.<key>`) instead of `JoinName.attribute`;
  - `scope` and AC6 are dropped;
  - the runtime parameters `filter` and `filterMode` replace `savedFilterIds[]`;
  - AC7 (saved views) follows with change 4.
- **REQ-046 AC3:** the filter parameters.
- **US-051:** the technical note "not persisted, lost on reload" becomes "part of the URL view state".
- **US-052:** AC1 and AC3 follow the permission rule above.
- **US-080 AC5 and AC6:** realized through this change.

Assumptions, open for the owner's review:

- **Activating a saved filter** replaces the filters in the bar with its expressions and operator. Further ad-hoc filters are added to them, with the operator of the bar. The query receives only `filter` and `filterMode`, not a saved filter ID.
- **Columns only:** filters apply to visible columns of the result, not to hidden or unconfigured attributes. A saved filter whose column is no longer shown is loaded without that expression, and the bar says so.
- **OR with join columns:**
  - an entry passes if any expression holds; for a join expression, at least one of its results must match;
  - a join column shows the results that match an expression on that join;
  - a join without an expression stays unfiltered.
- **Removing a join** removes the saved-filter expressions on it. A saved filter left without expressions is removed.
- **Limits:** a catalog has at most 50 saved filters.
- **Values:**
  - `contains` and `startsWith` ignore case;
  - `gt`, `lt`, `gte` and `lte` compare numbers and dates by value;
  - for an enum column, values are the stored enum values, and the bar shows their labels;
  - `in` and `notIn` take comma-separated values.

## Out of Scope

- **Saved views** with active filters (US-053, REQ-048, REQ-047 AC7): change 4.
- **Editing a saved filter in place:** a saved filter is removed and saved anew.
- **Filters on hidden attributes** and nested expression groups.
- **Performance targets for large repositories:** change 7 (#66).

## Capabilities

### New Capabilities

- `catalog/catalog-filters`: ad-hoc filters (operators, columns, join semantics, AND/OR, URL state) and saved filters (operations, validation, permissions, quick selection).

### Modified Capabilities

- `catalog/catalog-query`: "Pagination" counts only the rows that pass the filters.

## Impact

- `api/openapi.yaml`:
  - `addCatalogFilter` and `removeCatalogFilter` with `CatalogSavedFilterInput`;
  - `CatalogSavedFilter` and `CatalogFilterExpression` as implemented;
  - the parameters `filter` and `filterMode` of `queryCatalog`, and `filter` in `CatalogQueryProblem`.
- `backend/`:
  - `io.oea.app.catalog`: filter parsing and validation, saved filters in `config.savedFilters`;
  - `io.oea.core.catalog`: filter conditions in the page, count and aggregate statements;
  - no migration.
- `frontend/`:
  - the filter bar, the save dialog and the quick selection in SCR-020;
  - the URL state;
  - stories and messages in English and German.
- `manual/`: "Catalogs" in English and German.
- Documentation:
  - `business-objects/catalog.md` (SavedFilter, FilterExpression, BR-04), `docs/data-model.puml`, `docs/audit-event-catalog.md`;
  - US-051, US-052, US-080, REQ-046, REQ-047 and REQ-064;
  - the sprint plan.
