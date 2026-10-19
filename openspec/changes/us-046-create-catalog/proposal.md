# Proposal

## Why

After change 5 the repository contains entities, but there is no way to look at them: walking-skeleton steps 7 and 8 ([docs/walking-skeleton.md](../../../docs/walking-skeleton.md)) need a persisted catalog whose primary entity type selects the rows. This change adds the catalog as a configuration object and its creation; the table of catalog data follows in the next change (US-049).

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-06: Create, Configure and Use an Architecture Catalog](../../../requirements/use-cases/UC-06-create-and-use-catalog.md) (basic flow steps 1–5, A4) |
| User Stories | [US-046: Create a Catalog](../../../requirements/user-stories/US-046-create-catalog.md) (AC1–AC4, AC4 adapted to ADR-032, see below) |
| Requirements | [REQ-043: Create a catalog](../../../requirements/req/REQ-043-create-catalog.md) (AC1–AC5, AC5 adapted to ADR-032) |
| ADRs | [ADR-032](../../../adrs/ADR-032-authorization-model.md) (`catalog:manage` for shared catalogs, personal objects with `entity:read` and ownership), [ADR-033](../../../adrs/ADR-033-audit-trail-hardening.md) (audit event), [ADR-030](../../../adrs/ADR-030-postgresql-only.md) (catalog configuration as JSONB), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md) (screen stories) |
| Sprint / Milestone | S2 / Milestone 1, walking skeleton 2/2 ([Sprint plan](../../../docs/sprint-plan.md)) |

Walking-skeleton change 6. Owner decisions of 2026-10-19:

- US-046 and US-049 are two changes; this one ends with the catalog in the overview and an empty catalog page.
- After saving, the client navigates to the new catalog's page instead of the column configurator (US-047 follows in S8).
- `defaultJoinMode` is stored (default `aggregate`) but has no effect until joins exist; the form shows it as a plain choice.
- The screen stories are created inside this change (ADR-031).
- Permissions follow ADR-032: creating a catalog with `scope=instance` requires `catalog:manage`; a catalog with `scope=personal` can be created by every principal holding `entity:read`. This deviates from US-046 AC4 and REQ-043 AC5 (viewer gets 403 for every creation); both are adjusted in this change.
- Creating a catalog writes the audit event `catalog.created`.

## What Changes

- **Catalog persistence**: table `catalogs` with name, description, primary metatype, scope, default join mode, creator and modification fields and an empty JSONB configuration (columns, join definitions, saved filters, saved views); the name is unique per instance, compared case-insensitively after trimming.
- **Create a catalog** (`POST /catalogs`): validates the mandatory fields, the length limits, the primary metatype against the instance metamodel and the name collision; answers 201 with the created catalog including empty configuration lists.
- **Read catalogs** (`GET /catalogs`, `GET /catalogs/{id}`): the overview lists all instance catalogs and the caller's own personal catalogs, ordered by name; another person's personal catalog is answered with 404.
- **Authorization**: `scope=instance` requires `catalog:manage`, `scope=personal` requires `entity:read`; reading requires `entity:read`.
- **Audit**: `catalog.created` with the catalog as target.
- **Frontend**: catalog overview page with "New catalog" (visible to principals with `entity:read`; the scope `instance` only selectable with `catalog:manage`), the creation form as a modal with an inline name-collision message, the metatype dropdown loaded from `GET /metamodel/metatypes`, and a catalog page showing name, primary entity type and scope with an empty-state placeholder for the table of US-049; a "Catalogs" entry in the app header.
- **Screens**: story for SCR-020 (catalog browser: catalog list and empty catalog) and a new screen SCR-028 (create-catalog dialog).
- **Screenshot workflow** (ADR-031 §3/§4, REQ-192 AC2; owner decision 2026-10-19 during implementation): `.github/workflows/screens.yml` renders the screen images on every push to `main` with a read-only render job and a separate commit job; Storybook is published to GitHub Pages once the repository variable `STORYBOOK_PAGES` is set. The first images of SCR-020 and SCR-028 are rendered with the same script and committed in this change.
- **Requirements**: US-046 AC4 and REQ-043 AC5 rewritten to the ADR-032 rule; UC-06 A4 references the rule.

## Out of Scope

- Displaying entities in the catalog (US-049, next change); the existing `/views/catalog` operations of the contract stay unimplemented and are reconciled there
- Editing, renaming, promoting (`personal` → `instance`) and deleting catalogs (UC-06 A3, A4 step 3)
- Columns, join definitions, saved filters and saved views (US-047, US-048, later stories); the column configurator after saving
- Placing a catalog in the navigation tree (TreeNode, UC-06 step 15)
- Portal access to catalogs (`/portal/catalog`, `portal:read`)

## Capabilities

### New Capabilities

- `catalog/catalog-definition`: catalogs as configuration objects – creation with validation and name uniqueness, scope-dependent permission, visibility of personal catalogs, the catalog overview and reading a single catalog, audit of the creation

### Modified Capabilities

- none

## Impact

- `backend/`: catalog persistence in `io.oea.core`, creation and visibility rules in `io.oea.app.catalog`, `CatalogController` in `io.oea.api.catalog`; `AuditWriter` used with the new event type
- Flyway: `db/migration/V7__catalogs.sql`
- `api/openapi.yaml`: new paths `/catalogs`, `/catalogs/{id}`, schemas `Catalog`, `CatalogCreate`, `CatalogSummary`, tag `Catalogs`
- `frontend/`: pages, router entries, API module, header link; screen stories `SCR-020`, `SCR-028`; render script `scripts/render-screens.mjs` and the dev dependency `playwright`
- CI: new workflow `.github/workflows/screens.yml`; ADR-031 gains an implementation note
- Docs: `docs/data-model.puml` (`catalogs`), `business-objects/catalog.md`, `docs/audit-event-catalog.md` (`catalog.created`), `docs/screens/SCREENS.md`, `docs/walking-skeleton.md` (step 7), US-046, REQ-043, UC-06
