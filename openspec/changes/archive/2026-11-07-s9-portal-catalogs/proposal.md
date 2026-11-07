# Proposal

## Why

The web portal is meant for readers who hold only `portal:read`, for example the built-in role `portal-reader`. These readers cannot use it yet, for two reasons:

- `GET /portal/catalog` still uses the schemas of the removed `/views/catalog` operation and is not implemented ([#67](https://github.com/open-enterprise-architecture/OEA/issues/67)).
- The operator setup ships only the backend. The portal runs only on the Vite development server, and its delivery and security headers are undefined ([#76](https://github.com/open-enterprise-architecture/OEA/issues/76), finding WS-03). The condition of WS-03 is that this is resolved before the portal is delivered to operators.

This is S9 change 6 of the cut of 2026-11-05 (see `us-048-catalog-joins`): the portal (#67, #76). No user story covers the portal catalog yet, so the two issues and REQ-187 and REQ-160 serve as the backlog items.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-06: Create, Configure and Use an Architecture Catalog](../../../requirements/use-cases/UC-06-create-and-use-catalog.md) (readers use the catalog) |
| Requirements | [REQ-187: HTTP Security Headers, CORS, CSRF and Cookie Attributes](../../../requirements/req/REQ-187-http-security-baseline.md) (AC1 for portal responses), [REQ-160: Property Visibility Enforced on Every Read Channel](../../../requirements/req/REQ-160-visibility-on-all-read-channels.md) (portal channel), [REQ-124: Extend PropertyDefinition with visibilityMode](../../../requirements/req/REQ-124-propertydefinition-visibilitymode.md) |
| Issues | [#67: align /portal/catalog with the catalog query](https://github.com/open-enterprise-architecture/OEA/issues/67), [#76: production delivery and security headers of the web portal (WS-03)](https://github.com/open-enterprise-architecture/OEA/issues/76) |
| ADRs | [ADR-013: API Style](../../../adrs/ADR-013-api-style.md) (spec-first), [ADR-032: Authorization Model](../../../adrs/ADR-032-authorization-model.md) (§8), [ADR-036](../../../adrs/ADR-036-dev-test-identity-provider.md) (§3 operator setup), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md), [ADR-045: User Manual](../../../adrs/ADR-045-user-manual.md) |
| Security review | [Re-review of the walking skeleton](../../../security-reviews/walking-skeleton-2026-10.md), finding WS-03 |
| Screen | SCR-050 Portal home page / catalog overview (legacy mockup, becomes a story) |
| Sprint / Milestone | S9 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

Owner decisions of 2026-11-07:

- **The backend serves the portal.**
  - The backend image contains the built portal and serves it from the same origin as the API.
  - The security headers come from the same Spring Security configuration and are tested the same way.
  - There is no new component in `compose.yaml`, so no ADR is needed (#76).
- **A portal query per catalog replaces `/portal/catalog`.**
  - `GET /portal/catalogs/{id}/query` answers with the same `CatalogQueryResult` as `/catalogs/{id}/query`.
  - It needs `portal:read` and serves only catalogs with scope `instance`.
  - Like the catalog query, it shows only properties with visibility `public` (REQ-160).
  - The schema `CatalogFilterOptions` is removed (#67).
- **SCR-050 at minimum size.**
  - Readers with `portal:read` see an overview of the shared catalogs.
  - They can open a catalog read-only, with paging and sorting.

Further scope:

- **List:** `GET /portal/catalogs` lists the catalogs with scope `instance` as `CatalogSummary`, sorted by name.
- **Query:**
  - `page`, `pageSize` and `sort` work as in the catalog query;
  - the catalog's configured columns, default sort and default join mode apply;
  - a personal catalog, or a catalog that does not exist, answers 404.
- **Delivery:**
  - The backend image gets a build stage for the frontend.
  - The backend serves the built files when a portal directory is configured, which the image does. The development setup keeps the Vite server.
  - A `GET` on a path outside `/api` that matches no file gets `index.html`, so links into the portal work after a reload. Paths under `/api` keep their 404 problem.
- **Headers of portal responses (REQ-187 AC1):**
  - the same `Strict-Transport-Security`, `X-Content-Type-Options`, `Referrer-Policy` and `frame-ancestors 'none'` as the API;
  - a content security policy with `default-src 'self'`;
  - `index.html` is never cached; the hashed files under `/assets/` are cached for a year;
  - the portal files are readable without a session, because the login runs in the portal.
- **Portal UI:**
  - the page "Portal" (`/portal`) with the list of shared catalogs, and the read-only catalog page `/portal/catalogs/:id`;
  - a reader without `entity:read` lands on `/portal` and sees only the portal entry in the header;
  - the story for SCR-050, and messages in English and German.
- **Manual:**
  - a new page "Portal" in English and German;
  - the installation page states that the backend serves the portal and that it must sit behind TLS.

Realigned sections, which were non-binding until this change:

- **`/portal/catalog`** (`getPortalCatalogView`, planned): replaced by `listPortalCatalogs` and `queryPortalCatalog`.
- **`/portal/catalog/{id}`** (`getPortalEntityDetail`, planned, SCR-051): moves to `/portal/entities/{id}`, so it no longer looks like a catalog path. It stays planned.
- **SCR-050:** the mockup shows a filter panel with facets, a grid mode and a global search. This change implements the list and the read-only table. The rest follows with later portal work.

Assumptions, open for the owner's review:

- **No filters and no views in the portal query yet.** The parameters `filter`, `filterMode`, `joinMode` and `view` stay with the work API; the portal shows the catalog as configured.
- **Rows are not links yet.** The entity detail SCR-051 is still planned.
- **No navigation tree in the portal.** The tree (`GET /views/explorer`) needs `entity:read`, and the portal shows the flat list of shared catalogs.
- **Inline styles:** if the built portal needs `style-src 'unsafe-inline'` (style attributes or style elements of Nuxt UI), it is allowed for styles only. Scripts stay `'self'`.

## Out of Scope

- The portal entity detail SCR-051, diagrams SCR-052, dashboards SCR-053 and audit events SCR-054.
- Filters, saved views and the navigation tree in the portal.
- Facets, grid mode and global search of the SCR-050 mockup.
- Delivery of the Electron client (unchanged).

## Capabilities

### New Capabilities

- `portal/portal-catalogs`: reading the shared catalogs through the portal API (list and query), who may read them, and the portal pages SCR-050 that show them.
- `platform/portal-delivery`: how the backend serves the built web portal, including the fallback to `index.html`, its security headers and caching.

### Modified Capabilities

None. The catalog query (`catalog/catalog-query`) and the API security headers (`identity/sessions`) stay as they are; the portal query reuses the catalog query.

## Impact

- `api/openapi.yaml`:
  - `listPortalCatalogs` (`GET /portal/catalogs`) and `queryPortalCatalog` (`GET /portal/catalogs/{id}/query`);
  - `getPortalCatalogView` and `CatalogFilterOptions` removed;
  - `getPortalEntityDetail` moved to `/portal/entities/{id}`.
- `backend/`:
  - a portal controller in `io.oea.api.portal`, reusing the catalog query of `io.oea.app.catalog`;
  - serving the portal files and their headers in `io.oea.config`;
  - `backend/Dockerfile` with a frontend build stage.
- `frontend/`:
  - the pages `PortalPage` and `PortalCatalogPage`, their routes, the redirect for readers without `entity:read` and the portal entry in the header;
  - the story for SCR-050 and messages in English and German.
- `manual/`: a new page "Portal" and the installation page, in English and German.
- Documentation:
  - `docs/screens/SCREENS.md` (SCR-050 from `legacy-mockup` to `implemented`, legacy SVGs replaced);
  - REQ-187 and REQ-160 (implementation notes);
  - the sprint plan; issues #67 and #76 are closed by the implementation PR.
