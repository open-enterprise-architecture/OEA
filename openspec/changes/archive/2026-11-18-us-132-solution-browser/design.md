# Design

## Context

- **Sidebar:** `AppSidebar.vue` holds the navigation tree (`NavigationTree.vue`, SCR-055), read through `getExplorerView` (`GET /views/explorer`, tag `Views – Navigation`).
- **Solutions:** solutions (V25) carry `status` and, in plateau mode, `from_plateau_id` and `to_plateau_id`. Plateaus (V37) carry `status`, `valid_from` and their predecessor `succeeds`. "Active" is `status NOT IN ('implemented', 'archived')`, as in `PlateauStore` and `DeltaValueStore`.
- **Deltas:** deltas (V26) exist for element entities only. A delta `new` carries `metatype_id` and `name`, and gets an `entity_id` only at the go-live (V35); `modified` and `retiring` name an entity.
- **Entities:** element and connection entities are told apart by `metatypes.is_connection`, as in `NavigationTreeStore` and `PlateauContentStore`. "Not deleted" is `deleted_at IS NULL`; `metatypes.abbreviation` is optional.
- **Catalogs:** a reader sees instance catalogs and their own personal ones; `CatalogStore` already has this predicate (`VISIBLE`).
- **Paging:** paged lists answer `{ items, page: PageMeta }` with `totalElements` and `totalPages`, and take `PageParam` and `PageSizeParam`.

No schema change is needed.

## Goals / Non-Goals

**Goals:** the browser tab with its top level, counts and paged category contents, read-only, as specified in `specs/navigation/solution-browser`.

**Non-goals:**

- nesting of components (change 2);
- building blocks and diagrams;
- drag and drop from the browser into the navigation tree;
- the Electron client.

## Decisions

### D1 – Contract (delta to `api/openapi.yaml`, tag `Views – Navigation`)

The paths sit beside `/views/explorer`, the other view of the sidebar (ADR-020).

**`getBrowserView` – `GET /views/browser`, `entity:read`:**

```yaml
Browser:
  required: [currentState, plateaus, projects]
  currentState: { components: int, connections: int, catalogs: int }
  plateaus:     [ BrowserPlateau ]   # transition/target, order of the plateau overview
  projects:     [ BrowserSolution ]  # active project solutions, by name
BrowserPlateau:  { id: uuid, name, status: PlateauStatus, solutions: [BrowserSolution] }
BrowserSolution: { id: uuid, name, status: SolutionStatus, components: int }
```

Plateaus come in the order of the plateau overview (REQ-085, `PlateauStore.list()`): by `validFrom`, then by name. A chain of predecessors can branch, so it gives no total order. Plateaus with status `baseline` and `realized` are left out. A plateau's count is the number of its solutions.

**`listBrowserItems` – `GET /views/browser/items`, `entity:read`:**

Parameters:

- `scope`: `current-state` or `solution`, required;
- `solutionId`: uuid, required exactly when `scope=solution`;
- `category`: `components`, `connections` or `catalogs`, required;
- `page` and `pageSize`, using the shared `PageParam` and `PageSizeParam` (1–200, default 50).

```yaml
BrowserItemPage: { items: [BrowserItem], page: PageMeta }
BrowserItem:
  kind: entity | catalog | delta
  id: string          # entity ID, catalog UUID or delta UUID
  name: string
  metatypeAbbreviation: string | null   # entities and deltas
  entityId: int | null                  # deltas with an entity: modified, retiring, new after go-live
  solutionId: uuid | null               # deltas
  deltaType: SolutionDeltaType | null
```

Errors:

- **400:** a missing `scope` or `category`, an unknown enum value, or a `pageSize` outside 1–200.
- **400:** a `solutionId` that is not a UUID.
- **422 naming `solutionId`** (problem type `urn:oea:problem:validation-failed`, `invalidFields`): `solutionId` is missing with `scope=solution`, or given with `current-state`.
- **422 naming `category`** (same problem type): `connections` or `catalogs` with `scope=solution`.
- **404:** the solution is unknown.

Both operations list 400, 401, 403, (404, 422 for the items) and 429, and carry `x-oea-authorization` with `entity:read`.

### D2 – Stores (ADR-051)

All SQL lives in `core`, in the store of the feature that owns the table (ADR-051 §3); there is no browser store that reads the tables of other features.

- **`core/entity/EntityStore`:** count and page of the entities that are not deleted, for elements (`NOT m.is_connection`) or connections (`m.is_connection`), joined with `metatypes` for the abbreviation.
- **`core/catalog/CatalogStore`:** count and page of the catalogs with the existing `VISIBLE` predicate (`scope = 'instance' OR created_by = :person`).
- **`core/solution/SolutionBrowseStore` (new):** the active solutions with `to_plateau_id` and their delta count from one grouped query (`LEFT JOIN` on `entity_deltas` … `GROUP BY s.id`), never one query per solution; whether a solution exists; a page of the deltas of one solution.
- **Plateaus:** `PlateauStore.list()`, filtered to `transition` and `target` in `app`; no new SQL.

**Pages:**

- Ordered by `lower(name), id`, with `LIMIT/OFFSET`.
- A delta `modified` or `retiring` takes its name and metatype from the entity, even when the entity was deleted meanwhile; a delta `new` takes them from the delta, also after the go-live set its `entity_id`. The rule follows `delta_type`, not `entity_id`.

### D3 – Service and controller (`app/navigation/Browser`, `api/navigation/BrowserController`)

- The service checks the scope and category rules of D1 and resolves the solution (404).
- Responses are built from the store rows. Nothing is written and nothing is audited, because these are reads like `getExplorerView`.

### D4 – Frontend

- **`AppSidebar.vue`:**
  - tabs "Browser" and "Navigation tree" (Nuxt UI `UTabs`);
  - the active tab is kept in a module-level ref for the session (US-132 AC1), not in `localStorage`;
  - without `entity:read` the sidebar shows neither tab, as today.
- **New `components/SolutionBrowser.vue`** (beside `NavigationTree.vue`):
  - loads `getBrowserView` once when shown;
  - each category loads `listBrowserItems` lazily on its first expansion, with "Load more" while fewer items are loaded than `page.totalElements`;
  - an empty category or plateau gets the greyed style (`text-muted`) and the count `(0)`;
  - entity icons show `metatypeAbbreviation` as a small badge;
  - a delta row shows its type with a badge: new, modified, retiring.
- **Links:**
  - entity → `/entities/{id}/history`;
  - catalog → `/catalogs/{id}`;
  - solution and delta → `/solutions/{solutionId}`;
  - built from type and ID only, as `itemRoute` in `src/api/navigation.ts` does.
- **Names:** rendered only through text interpolation, never `v-html`.
- **Supporting files:**
  - `src/api/navigation.ts` with the two calls;
  - the test and story fake `navigationApi` in `src/test/navigation.ts` serves both operations;
  - SCR-055 stories show both tabs: with plateaus, without plateaus, and an empty category.

### D5 – Documentation and data model

- **Manual:** "Navigation" (`manual/usage/navigation.md`, `manual/de/usage/navigation.md`) gets the section "Browser", in English and German.
- **`SCREENS.md`:** SCR-055 is extended by the tabs and the browser.
- **Stories:**
  - US-132 is set to `done`;
  - US-133 to `in-progress` (AC3 waits for the building blocks).
- **Realignment notes:**
  - REQ-138 to REQ-140: categories that exist, the group "Projects", no status `in-progress` mapping;
  - UC-13: the browser tab.
- **Data model:** no data-model change; `business-objects/tree-node.md` notes that the browser is computed, not stored.

### D6 – Security

- **Authorization:** both operations need `entity:read`, like `getExplorerView` and `listSolutions`.
- **Visibility:** the browser shows names only, no property values, so property visibility (ADR-035) does not apply. Catalog visibility follows the personal-scope rule of `listCatalogs`; another person's personal catalog never counts and never lists.
- **Input limits:**
  - `pageSize` 1–200, with boundaries 1 and 200 accepted, 0 and 201 rejected;
  - `page` ≥ 0, with 0 accepted and −1 rejected;
  - `solutionId` must be a UUID (400);
  - enum parameters are validated by the contract (400).
- **Cost:** counts are aggregates over indexed columns; the solution counts come from one grouped query, so the top level costs a constant number of queries regardless of the number of solutions.
- **Output encoding:** names are rendered as text in the sidebar (no `v-html`), as required for the navigation tree.
- **Audit:** none, because these are reads.

## Risks / Trade-offs

- **Count cost:** "Current state" counts scan all entities. With the 50 000-entity target of the catalog latency NFR, a `count(*)` on the partial index stays in the low milliseconds; no caching for now.
- **Delta names:** a delta `modified` of an entity renamed later shows the current name, as the solution page does.

## Migration Plan

None. The change only adds endpoints and UI.

## Open Questions

None.
