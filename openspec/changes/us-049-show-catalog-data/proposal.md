# Proposal

## Why

After change 6 a catalog can be created, but its page shows only a placeholder: walking-skeleton step 8 ([docs/walking-skeleton.md](../../../docs/walking-skeleton.md)) needs the entities of the catalog's primary entity type as a sortable table. This is the last change of the walking skeleton; with it the end-to-end slice bootstrap → login → metamodel → entities → catalog table is complete.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-06: Create, Configure and Use an Architecture Catalog](../../../requirements/use-cases/UC-06-create-and-use-catalog.md) (A1 steps 1–3) |
| User Stories | [US-049: Display and Sort Catalog Data](../../../requirements/user-stories/US-049-show-catalog-data.md) (AC2–AC5; AC1 without joins, AC6 later) |
| Requirements | [REQ-046: Execute Catalog Query](../../../requirements/req/REQ-046-catalog-query.md) (AC5, AC6; AC1–AC4 with joins and filters later) |
| ADRs | [ADR-013](../../../adrs/ADR-013-api-style.md) (spec-first contract), [ADR-022](../../../adrs/ADR-022-structured-property-model.md) (typed property values), [ADR-019](../../../adrs/ADR-019-soft-delete-strategy.md) (deleted entities excluded), [ADR-032](../../../adrs/ADR-032-authorization-model.md) (`entity:read`, visibility of personal catalogs), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md) (screen story) |
| Sprint / Milestone | S2 / Milestone 1, walking skeleton 2/2 ([Sprint plan](../../../docs/sprint-plan.md)) |

Walking-skeleton change 7. Owner decisions of 2026-10-21:

- Pagination (US-049 AC5) is part of this change: server-side paging and a pagination control in the table, so that a catalog never answers with an unbounded list.
- The never-implemented operations `GET /views/catalog` and `GET /views/catalog/{id}` (generic entity list of the former SCR-020 mockup) are removed from the contract and replaced by `GET /catalogs/{id}/query`; the portal operation `/portal/catalog` stays for the web portal and is aligned later.

Owner decision of 2026-10-22:

- Going back to a catalog restores the previous view: sort and page are kept in the URL of the catalog page.

## What Changes

- **Catalog query** (`GET /catalogs/{id}/query`): returns the active entities of the catalog's primary metatype as rows with the values of the catalog's columns, sorted and paginated; the response names its columns so that the client renders them without knowing the metamodel.
- **Default columns**: as long as a catalog has no configured columns (US-047 follows in S8), the columns are `name` and, if the primary metatype maps a property named `Status`, `status`.
- **Sorting**: by any returned column, ascending or descending; without a sort parameter the rows are ordered by name ascending; entities without a value sort last.
- **Pagination**: `page` (0-based) and `pageSize` (default 50, maximum 200) with page metadata, following the existing paging conventions of the contract.
- **Visibility**: reading the query requires `entity:read`; another person's personal catalog answers 404 as for reading the catalog itself; soft-deleted entities and properties that are not `public` are not returned.
- **Frontend**: the catalog page shows the table instead of the placeholder: sortable column headers that cycle ascending → descending → default with an arrow indicator, the empty state of US-049 AC4 and a pagination control ("Page 1 of 3"); sort and page are part of the page URL, so back navigation and reload restore the view.
- **Contract cleanup**: `GET /views/catalog` and `GET /views/catalog/{id}` removed; **BREAKING** for the contract only (no client or server implements them).
- **Screens**: the SCR-020 story shows the table; SCR-020 and SCR-028 move to `implemented`.

## Out of Scope

- Join definitions and join modes (US-049 AC1 join column, REQ-046 AC1, AC2, AC4), filters and saved filters (REQ-046 AC3), saved views (US-049 AC6); US-049 stays `in-progress` until then
- Configured columns and their labels (US-047); until then every catalog shows the default columns
- Properties with visibility `role-restricted` or `connection-scoped` in catalog rows (REQ-160); they are omitted until the visibility feature
- The portal catalog (`/portal/catalog`, `portal:read`) and entity details from the table (SCR-012)
- Performance targets for large repositories (REQ-071, NFR to follow); the query uses the existing indexes

## Capabilities

### New Capabilities

- `catalog/catalog-query`: the live query of a catalog – rows of the primary metatype with column values, default columns, sorting, pagination and the visibility rules of the result

### Modified Capabilities

- none

## Impact

- `backend/`: query in `io.oea.core.catalog`, query service with column resolution in `io.oea.app.catalog`, `queryCatalog` in `CatalogController`
- Flyway: none (indexes of V6 suffice; see design)
- `api/openapi.yaml`: new `/catalogs/{id}/query` with `CatalogQueryResult`; `/views/catalog` and `/views/catalog/{id}` removed; description of `/portal/catalog` no longer refers to them
- `frontend/`: catalog table on `CatalogPage`, API function, screen story SCR-020
- Docs: `docs/screens/SCREENS.md`, `docs/walking-skeleton.md` (step 8 and status), US-049, REQ-046 (paging and sort parameters of the contract), `business-objects/catalog.md` (default columns)
