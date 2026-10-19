# Design

## Context

Migrations V1–V6 cover roles, audit, bootstrapping, identity, sessions, the metamodel and entities; there is no catalog table. `docs/data-model.puml` (package *Navigation & Presentation*) models `catalogs` with `name {UQ}`, `primary_metatype_id → metatypes.id`, `default_join_mode`, `scope`, `created_by`/`last_modified_by → persons.id` and a JSONB `config` for columns, joins, saved filters and saved views (ADR-030). `business-objects/catalog.md` describes `primaryEntityType` as an EntityType *name* and requires at least one column, while REQ-043 creates the catalog without columns.

`api/openapi.yaml` has no catalog resource. It contains `getCatalogView` (`GET /views/catalog`), a paginated entity list from the earlier SCR-020 mockup, and the portal variant; neither is implemented. Authorization is contract-driven: every operation declares `x-oea-authorization`; `conditions` are not enforced generically, and `OperationCoverageCheck` refuses to start if an implemented operation declares conditions unless the handler carries `@ConditionsNotReachable`.

`catalog:manage` is held only by `enterprise-architect`; `entity:read` by `viewer`, `analyst`, `enterprise-architect` and `integration-writer`. The system admin account holds neither, so only persons create catalogs. `GET /metamodel/metatypes` (`entity:read`) returns the metatypes of the instance configuration; `/me` returns the effective permissions for UI adaptation.

The frontend has a router with start, setup, login and profile pages, an `AppHeader`, a session store with `/me`, API modules per area and no screen stories yet (`frontend/src/screens/` is empty).

## Goals / Non-Goals

**Goals:**

- A catalog can be created, listed and read with the visibility and permission rules of the spec
- The catalog page exists as the target after creation, so that US-049 only adds the table

**Non-Goals:**

- Generic enforcement of contract `conditions` for all operations (only this operation's condition, D3)
- Any editing or deletion of catalogs, configuration of columns or joins

## Architecture Constraints

- ADR-028: `io.oea.api.catalog` (controller), `io.oea.app.catalog` (creation, visibility), `io.oea.core.catalog` (JDBC repository)
- ADR-030: catalog configuration in a JSONB column; PostgreSQL-only constructs allowed (functional unique index)
- ADR-032 §3, §4, §8: `catalog:manage` for shared catalogs; personal objects with `entity:read` and an object-level ownership check (L3); identical 404 for "not existing" and "not permitted"; generic 403 body
- ADR-033: `catalog.created` appended to the hash chain in the creating transaction, immediately before commit
- ADR-013: contract first; the frontend types are generated from `api/openapi.yaml`
- ADR-031, ADR-040: screen stories with real Nuxt UI components in `frontend/src/screens/`

## Decisions

### D1 – Table `catalogs`

V7 creates `catalogs` as modeled, with these refinements:

- `name TEXT NOT NULL CHECK (btrim(name) <> '' AND length(name) <= 255)`, stored trimmed; uniqueness through `CREATE UNIQUE INDEX catalogs_name ON catalogs (lower(btrim(name)))` instead of a plain `UNIQUE` (REQ-043: case-insensitive, trimmed)
- `description TEXT NOT NULL DEFAULT '' CHECK (length(description) <= 1000)` (empty instead of NULL, as for entities)
- `scope` and `default_join_mode` with `CHECK` constraints on the enum values
- `config JSONB NOT NULL DEFAULT '{"columns":[],"joinDefinitions":[],"savedFilters":[],"savedViews":[]}'`
- `last_modified_at`/`last_modified_by` set to the creation values
- grants for `oea_app`: `SELECT, INSERT, UPDATE` (no `DELETE`: deleting catalogs follows with ADR-019 decisions)

Instance scoping (`instance_id` in REQ-043) is not needed: the database holds exactly one instance, as for all other tables.

*Alternative:* a separate table per configuration list – rejected by ADR-030 and the data model; the lists are only read as a whole with the catalog.

### D2 – Primary metatype by ID

The API takes and returns `primaryMetatypeId` (UUID) plus the read-only `primaryMetatypeName`, like `EntityWrite.metatypeId`; the table references `metatypes.id`. REQ-043 and the business object speak of the EntityType *name*. Identity and label are separated: a metatype is identified by its UUID, its name is a mutable label. Renaming is a legitimate metamodel change and always carries the existing UUID – a package re-import matches metatypes by ID and updates the name (`PackageImporter`, change 4); a rename without the UUID would be a new metatype and collide by name. A reference by name would break on every rename, a reference by ID survives it. Any metatype of the instance configuration is allowed, including connection types (the business object does not restrict it). The validation error for an unknown ID names the field `primaryMetatypeId`.

*Alternative (input by name):* the API accepts the metatype name and resolves it to the ID on creation – rejected: the batch API already uses `metatypeId`, and the client form selects from the metatype list with IDs anyway.

### D3 – Scope-dependent permission as a contract condition

`createCatalog` declares `{"access": "permission", "permissions": ["entity:read"], "conditions": [{"when": "body field scope is 'instance'", "permissions": ["catalog:manage"]}]}`, the documented mechanism of ADR-032 §8. The application service checks the condition before validating anything else and answers with the same generic 403 problem the authorization filter produces (and the same `authz.denied` handling, if the filter audits denials).

Because conditions are not enforced generically, `OperationCoverageCheck` gets a second mark, `@ConditionsEnforcedByHandler("<reason>")`, accepted like `@ConditionsNotReachable`; `HandlerEnforcedConditionsTests` calls every operation with this mark as a holder of the mark's probe role with its probe body and expects the generic 403 (own database, because the probe roles come from IdP group mappings whose stored providers must not reach the shared test database).

*Alternatives:* `anyOfPermissions` plus an undocumented check in the service – rejected, the contract would not show the rule; generic condition enforcement in the filter – rejected for now, conditions need request bodies parsed before the controller and cover several unrelated operations (`deleted=true`, scoping connections).

### D4 – Name collision and validation order

The same separation of identity and label as in D2 applies to catalogs: a catalog is identified by its `id`, its name is a unique but mutable label. A later rename (UC-06 A3) goes through `/catalogs/{id}` and checks uniqueness against all other catalogs; every reference to a catalog (navigation tree `tree_node_items.reference_id`, saved views, portal links) uses the ID, never the name.

Order: contract validation of the request body (mandatory fields, maximum lengths, enum values; bean validation of the generated interface, before the handler), then in the service authorization (D3), the remaining field checks (blank name after trimming), metatype lookup, insert. A request that violates the contract is therefore answered with 422 even if the caller lacks `catalog:manage`; this reveals nothing beyond the contract. The name check is the unique index: the insert catches the unique violation of `catalogs_name` and answers 422 with problem type `urn:oea:problem:catalog-name-taken`, detail "A catalog with this name already exists" and `invalidFields: ["name"]`. A pre-check `SELECT` is not used, because it would race with a concurrent creation anyway. Other validation errors use `urn:oea:problem:validation-failed` with `invalidFields`, as the setup endpoint does. Personal and instance catalogs share the namespace (REQ-043), so a name collision also reveals that a personal catalog of another person with that name exists; this is accepted (the name is the only information) and documented in the spec of REQ-043.

### D5 – Visibility

`GET /catalogs` returns `scope = 'instance' OR created_by = :personId`, ordered by `lower(name)`, as `CatalogSummary` (ID, name, primary metatype ID and name, scope, creator display name, creation time). No pagination: the overview is small in the skeleton; pagination is added with the portal overview if needed. `GET /catalogs/{id}` returns the full `Catalog` or 404 if the catalog does not exist or is another person's personal catalog (ADR-032 L3, identical answer).

### D6 – Audit

`catalog.created`, category `change`, severity `info`, source `API`, actor the person, target `catalog` with the catalog ID; details `name`, `primaryMetatypeId`, `scope`. Written by `AuditWriter.append` in the creating transaction. A rejected creation writes nothing (as other 422 answers). Documented in `docs/audit-event-catalog.md`.

### D7 – Frontend

- `src/api/catalogs.ts` with `listCatalogs`, `getCatalog`, `createCatalog`, typed from the generated schema; problem bodies through the existing `problem.ts`
- `CatalogsPage.vue` (`/catalogs`): list of visible catalogs (name, primary entity type, scope badge), empty state, button "New catalog" when `/me` has `entity:read`
- `CreateCatalogModal.vue`: name, description, primary entity type (`USelect` from `GET /metamodel/metatypes`, ordered by `sortOrder`), scope (radio; `instance` disabled without `catalog:manage`, preselected `personal` then, otherwise `instance`), default join mode (radio, `aggregate` preselected). A `catalog-name-taken` problem is shown at the name field; other 422 fields at their inputs. On success the client navigates to `/catalogs/{id}`
- `CatalogPage.vue` (`/catalogs/:id`): header with name, primary entity type, scope; placeholder "The catalog table follows" area left for US-049; 404 shows "Catalog not found"
- `AppHeader` gains a "Catalogs" link for signed-in principals
- Texts in English, inline like the existing pages (i18n per ADR-025 follows with the i18n change)

### D8 – Screens

- SCR-020 "Catalog Browser": story with the states *catalog list with items*, *empty overview*, *empty catalog selected*; the entity table is added to this story by US-049. The legacy SVGs are replaced by the generated images; status `prototype` (the table part is still missing, so not `implemented` yet)
- SCR-028 "Create catalog dialog" (new, CA, UC-06, must): stories *default*, *viewer (instance disabled)*, *name collision*; status `prototype`, and `implemented` together with SCR-020 after US-049
- The stories use the real page and form components with the fetch function injected (as `StartPage.stories.ts`), not separate mock components; the creation form is `CreateCatalogForm.vue`, which `CatalogsPage` shows in a `UModal` (the form alone is testable without the modal's teleport)
- The image of a screen is rendered from the first story of its story file by `frontend/scripts/render-screens.mjs` (Playwright, light and dark); `.github/workflows/screens.yml` runs it on every push to `main` in a read-only job and commits changed images in a separate job (REQ-192 AC2); the first images are rendered with the same script and committed in this change because `scripts/check_screens.py` checks them before the merge
- Runtime templates of stories do not see the auto-imported Nuxt UI components; stories and the Storybook preview import them explicitly (`UCard`, `UApp`)

## Data Model

- `docs/data-model.puml`: `catalogs.name` documented as unique on `lower(btrim(name))`; `description` NOT NULL DEFAULT ''; CHECK constraints for `scope`, `default_join_mode`; default of `config` with the four empty lists; changelog comment for change 6
- `business-objects/catalog.md`: `primaryEntityType` referenced by metatype ID (name shown, D2); `columns` may be empty (default columns `name` and `status` in the display until US-047); name uniqueness case-insensitive and trimmed; note on personal/instance namespace
- Flyway: `db/migration/V7__catalogs.sql`

## API

Delta to `api/openapi.yaml`:

- New tag `Catalogs`
- `GET /catalogs` (`listCatalogs`, `entity:read`): `200` array of `CatalogSummary`
- `POST /catalogs` (`createCatalog`, `entity:read` + condition `catalog:manage` for `scope=instance`): body `CatalogCreate` (`name` 1–255, `description` ≤ 1000, `primaryMetatypeId` UUID, `scope` enum, `defaultJoinMode` enum default `aggregate`); `201` `Catalog` with `Location`; `422` problem with `invalidFields` (types `validation-failed`, `catalog-name-taken`); `401`, `403`
- `GET /catalogs/{id}` (`getCatalog`, `entity:read`): `200` `Catalog`; `404`
- Schemas: `CatalogScope`, `JoinMode`, `CatalogCreate`, `CatalogSummary`, `Catalog` (summary fields plus `description`, `defaultJoinMode`, `createdBy` (`PrincipalRef`), `createdAt`, `lastModifiedAt`, and `columns`, `joinDefinitions`, `savedFilters`, `savedViews` as arrays; their item schemas are `object` placeholders described as "follows with US-047/US-048", always empty in this change)
- `getCatalogView` and `getPortalCatalogView` unchanged; US-049 reconciles them with `/catalogs/{id}/query`

## Risks / Trade-offs

- [Shared namespace reveals names of other persons' personal catalogs] → only the existence of the name; required by REQ-043; noted in the spec
- [Handler-enforced condition can be forgotten when the operation changes] → the matrix test case per condition fails if the check is removed
- [Placeholder item schemas for columns and joins] → typed when US-047/US-048 define them; no client relies on the content yet
- [SCR-020 story without the table] → US-049 extends the same story; the status stays `prototype` until then

## Open Questions

- none
