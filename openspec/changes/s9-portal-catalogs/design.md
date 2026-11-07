# Design

## Context

- **Contract:**
  - `getPortalCatalogView` (`GET /portal/catalog`, planned) returns `EntityListItem` with `CatalogFilterOptions`, the shape of the removed `/views/catalog`.
  - `getPortalEntityDetail` (`GET /portal/catalog/{id}`, planned, SCR-051) returns `EntityDetail`.
  - All portal operations need `portal:read`. That permission is held by `portal-reader`, `viewer`, `analyst` and `enterprise-architect`.
- **Catalog query:**
  - `CatalogQueries.query` resolves the catalog through `Catalogs.get`, so another person's personal catalog answers 404.
  - It applies the configured columns, default sort and default join mode, and shows only properties with visibility `public` (spec `catalog/catalog-query`).
  - `CatalogController` maps the result to `CatalogQueryResult`.
- **Security headers:**
  - `SecurityConfiguration` writes HSTS, `nosniff`, `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY` and `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'` on every response.
  - Spring Security's default `Cache-Control: no-cache, no-store, …` applies too.
  - Every request outside the public operations needs a session.
- **Delivery:**
  - `backend/Dockerfile` builds only the jar, and `backend/src/main/resources/static` is empty.
  - The frontend is a Vite single-page app with `createWebHistory`. It calls the API under the relative path `/api`, which the development server proxies to the backend.
  - CI builds the image in the job "Security checks" and runs `scripts/compose_smoke_test.sh` against the operator setup.
- **Frontend:**
  - `CatalogsPage` and `CatalogPage` need `entity:read`, and `CatalogTable` renders a `CatalogQueryResult` with sort and paging events.
  - The header shows its entries by permission; the sidebar needs `entity:read` (us-054).
- **Browser checks:** the CI job "Frontend" installs Chromium for the story tests.

## Goals / Non-Goals

**Goals:**

- the backend image serves the built portal under the policy of REQ-187, from the same origin as the API;
- `listPortalCatalogs` and `queryPortalCatalog`, built on the catalog query;
- the portal pages SCR-050 for readers with `portal:read`.

**Non-Goals:**

- the other portal operations (SCR-051 to SCR-054);
- filters, views and joins switched at runtime in the portal;
- a separate web server or a reverse proxy in `compose.yaml`.

## Architecture Constraints

- **ADR-013:** spec-first. The contract changes in `api/openapi.yaml` before the code, and the generated `PortalApi` interface is implemented.
- **ADR-032 §8:** the permission comes from `x-oea-authorization`, and `check_openapi_authorization.py` checks it. Scope rules (instance only) live in the service.
- **ADR-036 §3:** the operator setup stays safe by default. Serving the portal adds no service and no secret.
- **ADR-028:** layers `api` (controller), `app` (service) and `core` (store). The portal reuses `app.catalog` and adds no store.
- **ADR-031, ADR-040:** SCR-050 becomes a story with real components, and its images are rendered by CI.
- **ADR-045:** a new manual page in English and German.
- **REQ-187, REQ-160:** headers on every response, and visibility on every read channel.

## Decisions

### D1 Portal service on top of the catalog query

- `io.oea.app.portal.PortalCatalogs` has two methods:
  - `list()` returns the catalogs with scope `instance`, sorted by `lower(name)` then id. This uses a new `CatalogStore.listShared()`.
  - `query(principal, id, page, pageSize, sort)` first checks that the catalog has scope `instance`, otherwise it throws `NoSuchElementException` (404). It then calls `CatalogQueries.query(principal, id, page, pageSize, sort, Map.of())`.
- Reusing the query keeps one place for columns, visibility, sort and the 400 `invalidParams`. A filtered copy of the query would drift.
- The check comes before the query, so Kurt's own personal catalog is 404 in the portal as well, as the spec requires.
- Alternative: give `/catalogs/{id}/query` `portal:read`. Rejected by the owner decision; the work API and the portal API stay apart.

### D2 Controller and mapping

- `io.oea.api.portal.PortalController` implements `PortalApi` for the two operations.
- The mapping from `CatalogQueries.Result` to `CatalogQueryResult` moves from `CatalogController` into a helper `io.oea.api.catalog.CatalogQueryMapping` with public static methods. Both controllers use it, so the two responses cannot differ.
- `getPortalEntityDetail` and the other portal operations stay planned (501 through `NotImplementedInterceptor`).

### D3 Serving the portal

- The property `oea.portal.directory` (env `OEA_PORTAL_DIRECTORY`) is empty by default, so no portal is served. The image sets `/app/portal`.
- `io.oea.config.web.PortalConfiguration` is active only when the directory is set.
- It registers a resource handler for `/**` on `file:{directory}/`, with a resolver that falls back to `index.html`. The fallback applies only to paths outside `/api/` that do not end in a file name with an extension.
  - A missing `/assets/x.js` stays 404, so a stale `index.html` never gets HTML as a script.
  - `/api/**` is matched by the controllers and keeps its problem responses, because the `/api` prefix is excluded from the handler.
- **Cache headers**, set by the handler:
  - `index.html`, including the fallback: `Cache-Control: no-cache`;
  - `/assets/**`: `Cache-Control: public, max-age=31536000, immutable`;
  - other files in the directory, such as `favicon.svg`: `no-cache`.
  - Spring Security's `cacheControl` writer is set to skip responses that already carry `Cache-Control`. Its default writer already skips them, because it writes its headers lazily on commit and checks for an existing header. A test pins this behavior.
- **Security:** `GET` and `HEAD` on every path outside `/api/**` are `permitAll` while the portal is active. The login page is part of the portal, and the files hold no data. The authorization interceptors stay on `/api/**`.
- A request outside `/api` with another method answers 405 or 404 from MVC. The CSRF origin filter stays unchanged.
- Alternative: copy `dist` into `classpath:/static` at build time. Rejected because the jar would depend on the frontend build, the backend tests would need it, and the development profile would serve stale files.

### D4 Headers per path

- `SecurityConfiguration` uses `DelegatingRequestMatcherHeaderWriter` for the content security policy:
  - `/api/**` keeps `default-src 'none'; frame-ancestors 'none'`;
  - every other path gets the portal policy `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'`.
- HSTS, `nosniff`, `Referrer-Policy` and `X-Frame-Options` stay global.
- `img-src data:` is needed for the inline SVG icons and the QR code of the TOTP enrollment (`QrCode.vue`).
- **Inline styles:**
  - Vue and Nuxt UI set styles through the CSSOM (`element.style`), which the CSP does not restrict.
  - If the smoke test (D6) reports a `style-src` violation from a `<style>` element or a `style` attribute in the markup, the portal policy gains `'unsafe-inline'` for `style-src` only, and the reason goes into the code comment.
  - Scripts never get `'unsafe-inline'` or `'unsafe-eval'`.
- The policy text is defined once, in `backend/src/main/resources/portal/content-security-policy.txt`. `SecurityConfiguration` reads it, and the frontend smoke test reads the same file.

### D5 Image

`backend/Dockerfile` gets a first stage `frontend`:

- It uses `node:24-bookworm-slim`, pinned by digest like the other base images.
- It copies `api/openapi.yaml`, `frontend/package*.json` and `frontend/`, then runs `npm ci` and `npm run build`.
- The runtime stage copies `frontend/dist` to `/app/portal` (owned by root, mode 0444 for files and 0555 for directories) and sets `ENV OEA_PORTAL_DIRECTORY=/app/portal`.

The development setup (`compose.dev.yaml`) does not set the variable and keeps the Vite server with the `/api` proxy. `.dockerignore` is an allowlist. It gains the frontend sources the build needs (`frontend/package*.json`, `frontend/index.html`, `frontend/src/`, `frontend/public/`, the Vite and TypeScript configuration and the build plugins). `node_modules`, `dist` and `storybook-static` stay out.

`scripts/check_production_artifact.py` gains two checks on the image: `/app/portal/index.html` exists, and no source map (`*.map`) is shipped. A source map is the only way the portal build could expose more than what the browser already receives. Vite does not emit source maps by default, and the check keeps it that way.

### D6 Tests of the delivery

- **Backend (`PortalDeliveryTests`, MockMvc with a temporary portal directory):**
  - `/` and `/portal/catalogs/{id}` give `index.html` with `no-cache`;
  - `/assets/app-abc.js` is immutable, and a missing asset is 404;
  - `/api/v1/unknown` gives a 404 problem;
  - the portal CSP is on portal paths and the API CSP on `/api`;
  - without the directory, `/` is 404.
- **Compose smoke test (`scripts/compose_smoke_test.sh`):** `GET /` from the operator setup answers 200 with `text/html` and the portal policy.
- **Browser smoke test (`frontend/scripts/csp-smoke.mjs`, `npm run test:csp`, in the CI job "Frontend" after the build):**
  - it serves `frontend/dist` through `vite preview` with the policy from D4 as header;
  - it answers `/api/v1/**` with canned responses for a portal reader: `me`, the portal catalogs and one query page;
  - it opens `/login`, `/portal` and `/portal/catalogs/{id}` in Chromium;
  - it fails on any `securitypolicyviolation` event or CSP console error.

### D7 Frontend

- **API:** `src/api/portal.ts` with `fetchPortalCatalogs()` and `queryPortalCatalog(id, { page, pageSize, sort })` on the generated types.
- **`PortalPage.vue` (`/portal`, SCR-050):**
  - a heading "Portal", and the list of shared catalogs with name and primary metatype as links;
  - an empty state "No shared catalogs yet", and an error state.
- **`PortalCatalogPage.vue` (`/portal/catalogs/:id`):**
  - the catalog name, then `CatalogTable` with sort and paging;
  - the view state in the URL (`page`, `sort`), as in `CatalogPage`;
  - no configure, filter, view or navigation actions, and no join mode switch: `CatalogTable` gains a prop `readOnly` that shows the join header without the switch.
  - A 404 shows "Catalog not found" with a link back to the portal.
- **Router guard:** a session with `portal:read` and without `entity:read` that opens `/`, `/catalogs` or `/catalogs/:id` is redirected to `/portal`. Pages without a session and the setup page are unchanged.
- **Header:** the entry "Portal" for `portal:read`. A reader without `entity:read` sees no catalogs entry and no metamodel entry, as before.
- **i18n:** `portal.*` keys in `en.json` and `de.json`.
- **Story:** `screens/SCR-050-portal-home.stories.ts` with the stories Overview, Empty, Catalog and NotFound. SCR-050 changes from `legacy-mockup` to `implemented`, and the legacy SVGs are deleted (ADR-031).

### D8 Manual

- A new page `manual/usage/portal.md` and its German counterpart: who uses the portal, opening a shared catalog, and that personal catalogs never appear there. It covers SCR-050.
- `manual/setup/installation.md` and its German counterpart: the backend serves the portal on the same port as the API, and it must be reached through TLS (`Secure` cookies, HSTS).

## Data Model

No change. The portal reads `catalogs` with `scope = 'instance'` through the existing store. `docs/data-model.puml` and `business-objects/catalog.md` stay as they are.

## API

Delta to `api/openapi.yaml` (tag `Portal`):

- **Removed:** `GET /portal/catalog` (`getPortalCatalogView`, planned) and the schema `CatalogFilterOptions`. Nothing else references it. `EntityListItem` stays, because `EntityDetail` builds on it.
- **New `GET /portal/catalogs`** (`listPortalCatalogs`):
  - `x-oea-authorization` `portal:read`;
  - 200 with an array of `CatalogSummary`; 401, 403 and 429.
- **New `GET /portal/catalogs/{id}/query`** (`queryPortalCatalog`):
  - `x-oea-authorization` `portal:read`;
  - parameters `UuidIdParam`, `PageParam`, `PageSizeParam` and `SortParam`;
  - 200 with `CatalogQueryResult`, 400 with `invalidParams` (`page`, `pageSize`, `sort`), 401, 403, 404 and 429;
  - the description states that only `instance` catalogs are served, with the configured columns, default sort and default join mode, and that the result equals the catalog query.
- **Moved:** `GET /portal/catalog/{id}` (`getPortalEntityDetail`) to `GET /portal/entities/{id}`. It stays planned.
- **Tag description `Portal`:** names SCR-050 as implemented and the remaining operations as planned.

## Risks / Trade-offs

- **The fallback hides typos in portal paths.** A wrong path outside `/api` gives the portal, which shows its own "not found" route. → `/api` keeps 404 problems, and assets with an extension are not rewritten.
- **The image build takes longer and needs npm in CI.** → The npm cache is a BuildKit cache mount, and the image is built only in the job "Security checks", as today.
- **A third-party component may need inline styles.** → The smoke test (D6) finds it before the merge, and the relaxation stays limited to `style-src`.
- **`permitAll` on non-API `GET`s.** → Only files of the portal directory and `index.html` are served, and the API stays deny-by-default. The backend test checks that `/api/v1/catalogs` without a session is still 401.
- **One policy file read by two builds.** → The smoke test reads the same file the backend serves, so the two cannot drift.
