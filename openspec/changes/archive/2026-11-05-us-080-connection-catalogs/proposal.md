# Proposal

## Why

A catalog lists entities of one primary metatype. For data lineage and interface inventories, the interesting entities are often the connections themselves. Lukas wants to list all data flows in a table, with the application at each end, and join the data objects each flow carries (US-080, REQ-065).

What exists already:

- A connection metatype can be chosen as the primary metatype; the query returns its connections as rows.
- A connection metatype may be an allowed endpoint of another one (US-036), so a join from a connection catalog (`data-flow` → `carries-data` → `data-object`) runs through the join logic of US-048 unchanged.

What is missing:

- The source and the target of each connection, as columns.
- A hint when no join can start at the connection metatype.

This is S9 change 2 of the cut of 2026-11-05 (see `us-048-catalog-joins`).

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-06: Create, Configure and Use an Architecture Catalog](../../../requirements/use-cases/UC-06-create-and-use-catalog.md) |
| User Story | [US-080: Catalog with Connection Type as Primary EntityType](../../../requirements/user-stories/US-080-catalog-join-connection-type-primary.md) AC1–AC4 |
| Requirements | [REQ-065: Catalog Join for Connection-Type Primaries](../../../requirements/req/REQ-065-n-connection-catalog-join.md), [REQ-044](../../../requirements/req/REQ-044-configure-columns.md) (columns), [REQ-046](../../../requirements/req/REQ-046-catalog-query.md) (query) |
| ADRs | [ADR-004](../../../adrs/ADR-004-reification-details.md) (one traversal level), [ADR-010](../../../adrs/ADR-010-n-connection-data-lineage.md) (n-connection), [ADR-044: View State in the URL](../../../adrs/ADR-044-view-state-in-url.md), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md), [ADR-045: User Manual](../../../adrs/ADR-045-user-manual.md) |
| Screen | SCR-020 Catalog Browser |
| Sprint / Milestone | S9 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

Owner decisions of 2026-11-05:

- **Endpoint columns:**
  - a column of a connection catalog can show an attribute of the connection's source or target: the system fields and the `public` properties;
  - `CatalogColumnConfig` gains `endpoint` (`source` or `target`);
  - the keys are `source.<key>` and `target.<key>`, for example `source.name`;
  - endpoint columns are sortable like other columns.
- **Offered properties:** all `public` properties mapped to an allowed endpoint metatype of that side, or to one of its subtypes. A side without a constraint offers all `public` properties of the instance metamodel. An endpoint entity that does not map the property shows an empty cell.
- **Default columns** of a connection catalog without configured columns: "Source", "Name" and "Target" (the names of source, connection and target), plus "Status" if it is mapped.
- **Hint in the dialog "Joins":** if no connection metatype starts or ends at the primary connection metatype, the dialog explains that the catalog shows the connections with their source and target only.
- **SCR-020:**
  - the dialog "Configure columns" lists the attributes of the source and the target as two further groups;
  - the create form groups the metatypes into elements and connections;
  - a new story "Connection catalog".
- **Manual:** "Connection catalogs" in "Catalogs", in English and German.

Realigned sections, which were non-binding until this change:

- **REQ-065:**
  - the virtual fields `sourceEntityName` and `targetEntityName` become endpoint columns with the keys `source.name` and `target.name`;
  - `allowsConnectionAsSource` is not a flag: the allowed endpoints of the connection metatype decide (US-036);
  - BR-04 (warning without joins) becomes the hint in the dialog "Joins";
  - the endpoint `GET /catalogs/{id}/data` is `queryCatalog` (`GET /catalogs/{id}/query`).
- **US-080 technical notes:** the same realignment.

Assumptions, open for the owner's review:

- **Only on connection catalogs:** an endpoint column on a catalog of an element metatype, and in the target columns of a join, is rejected with 422.
- **Deleted endpoint:** the cells of a deleted source or target stay empty, and the connection stays in the result.
- **Labels:** the header of an endpoint column is "Source" or "Target" for the name, and "Source: <attribute>" or "Target: <attribute>" otherwise, unless the column has its own label.

## Out of Scope

- **US-080 AC5 and AC6** (filters on source and target, and the GDPR example of REQ-064): with the filter change 3, as owner decision 2026-11-05.
- **Chained joins over more than one level** (ADR-004, REQ-065 v2.0).
- **Joins whose target is a connection metatype:** they are possible already, since the join's target columns are those of the target metatype. Endpoint columns of the join target are not part of this change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `catalog/catalog-columns`: "All attributes as columns" adds the source and target attributes of a connection catalog.
- `catalog/catalog-query`:
  - "Default columns" of a connection catalog;
  - a new requirement "Connections as rows", with the n-connection join.
- `catalog/catalog-joins`: a new requirement "Hint without a possible join".

## Impact

- `api/openapi.yaml`:
  - `CatalogColumnConfig.endpoint`;
  - the description of `CatalogColumn.key` and of the `sort` parameter.
- `backend/`:
  - `io.oea.app.catalog`: the column validation with the offered endpoint properties, the default columns of a connection catalog, and the sort keys;
  - `io.oea.core.catalog`: the store joins `entity_connections` and the endpoint entities;
  - no migration: the columns live in `catalogs.config.columns`.
- `frontend/`:
  - the columns dialog, the create form, the joins dialog hint, the column labels;
  - the story and the messages in English and German.
- `manual/`: "Catalogs" in English and German.
- Documentation:
  - `business-objects/catalog.md` (ColumnConfig `endpoint`, BR-11), `docs/data-model.puml`;
  - US-080, REQ-065, REQ-044 and REQ-046;
  - the sprint plan.
