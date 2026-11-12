# Design

## Context

See proposal.md – Why. The current state:

- **Solutions:** the table `solutions`, `listSolutions`, `getSolution` and the page SCR-061 exist since S10 change 5 (`us-038-create-solution`). A solution is always in project mode; the plateau columns are null.
- **Landscape:** the table `entities` holds the entities of all metatypes, element and connection metatypes alike (connections additionally in `entity_connections`, V19). The batch API and `createEntity` write it; `deleted_at` marks a deleted entity (ADR-019). No production code deletes entities yet (`deleteEntity` is `planned`); tests set `deleted_at` directly, as `CatalogQuerySteps` does.
- **Paging:** the catalog query pages with `page`, `pageSize` (1–200, default 50) and an opaque cursor from `CatalogCursors` (an HMAC over the sort keys of the last row, the fingerprint of the query and the next page number, keyed per backend start; S9, REQ-071).
- **Metatypes:** `listMetatypes` lists the instance metatypes with `is_connection` for `entity:read`.

## Goals / Non-Goals

**Goals:**

- A solution shows the current landscape, searchable, filterable by metatype and paged, as the base of the deltas of changes 2 and 3.
- The baseline reads the catalog directly, so it needs no new table and stays current without recomputation.

**Non-Goals:**

- No plateau mode, no connections in the baseline, no property values.
- No delta actions in the list (changes 2 and 3).

## Architecture Constraints

- **ADR-028:** `SolutionController` implements `getSolutionLandscape` of the generated `SolutionsApi`; `app/solution/SolutionLandscape` holds the query with plain JDBC.
- **ADR-032:** the operation declares `x-oea-authorization` with `entity:read`, like `getSolution`; the contract test checks it.
- **ADR-019:** a deleted entity (`deleted_at` set) is not part of the landscape.
- **ADR-031:** the story SCR-061 gets states for the baseline with play functions; CI renders the images.
- **ADR-033:** reading the baseline is not audited, as reading a solution is not.

## Decisions

### D1 – The catalog is the baseline (owner decision 2026-11-12)

- **Project mode:** the baseline is every entity with `deleted_at IS NULL` whose metatype has `is_connection = false`.
- **Current state:** the row as it is now, with its `version`. Nothing is aggregated over solutions.
- **S14:** the go-live applies the deltas of a solution to `entities`, so the baseline then shows the implemented state without a change to this query.
- **Alternative considered:** the aggregate of the deltas of implemented solutions (REQ-039 as written). Rejected by the owner: it ignores the landscape already in the catalog and stays empty until S14.

### D2 – `SolutionLandscape` query

```sql
SELECT e.id, e.name, e.version, m.id AS metatype_id, m.name AS metatype_name
FROM entities e
JOIN metatypes m ON m.id = e.metatype_id
WHERE e.deleted_at IS NULL AND NOT m.is_connection
  [AND e.metatype_id = :metatypeId]
  [AND strpos(lower(e.name), lower(:q)) > 0]
  [AND (lower(e.name), e.id) > (:cursorName, :cursorId)]
ORDER BY lower(e.name), e.id
LIMIT :pageSize
```

- **Search:** `strpos` on lower case, so `%` and `_` in the term are plain characters and need no escaping.
- **Total:** a `count(*)` with the same filters, without the cursor condition.
- **Paging:**
  - without a cursor, `OFFSET page * pageSize`;
  - with a cursor, the keyset condition above and no offset.
- **Solution:** looked up first; an unknown ID gives `NoSuchElementException` (404) before the landscape is read.
- **Alternative considered:** reusing `CatalogQueries` with a synthetic catalog. Rejected: a catalog has one primary metatype, columns and joins, while the baseline spans all element metatypes and needs none of them.

### D3 – Cursor

- **Codec:** `CatalogCursors`, with the fingerprint over `"solution-landscape"`, the solution ID, `q`, `metatypeId` and `pageSize`, and the keys `lower(name)` and `id` of the last entry.
- **Next cursor:** only when a following page exists. No length fallback is needed: `lower(name)` has at most 255 characters, so the cursor stays below 2000.
- **Invalid cursor:** a cursor that does not decode, belongs to another fingerprint or comes without `page` answers 400 `validation-failed` naming `cursor`, through a new `InvalidSolutionQueryException` mapped like `InvalidSolutionException`.
- **Reuse:** `CatalogCursors` stays in `app/catalog`; `SolutionLandscape` depends on it as a component.

### D4 – Limits

- **Contract:**
  - `q`: `maxLength: 255`;
  - `pageSize`: `PageSizeParam` (1–200);
  - `page`: `PageParam` (≥ 0);
  - `metatypeId`: `format: uuid`.
  
  Values outside these limits answer 400 by contract validation, as for the catalog query.
- **Blank `q`:** treated as no search.
- **Unknown metatype:** an empty page, not an error. The filter is a query parameter, not a reference that is stored.

### D5 – Frontend

- **Area "Baseline" on SCR-061** (`SolutionPage.vue`), below the architecture vision:
  - a `UInput` for the search, applied after 300 ms;
  - a `USelect` with the element metatypes from `listMetatypes` and "All metatypes";
  - a `UTable` with name and metatype;
  - `UPagination`.
- **Next page:** follows by the cursor of the current page. A page jump or a changed filter starts without a cursor.
- **Empty states:**
  - the notice "No entities in the current landscape – you can only create new entities" when the unfiltered baseline has the total 0;
  - "No entity matches" when only the filters match nothing.
- **Cursor rejected:** a 400 naming `cursor` repeats the query without the cursor, like the catalog table.
- **API client:** `getSolutionLandscape` in `src/api/solutions.ts`.
- **Test API:** the stateful fake API of `src/test/solutions.ts` gets a landscape.
- **i18n:** keys `solutions.baseline.*` in `en.json` and `de.json`.
- **Stories:** `SCR-061-solution.stories.ts` gets "Baseline", "Empty landscape" and "No match".

## Data Model

No migration and no new table.

- **`docs/data-model.puml`:** a note on `solutions` that the baseline in project mode is read from `entities` (owner decision 2026-11-12).
- **`business-objects/solution.md`:** the baseline note realigned, version 0.4.0.

## API

Delta to `api/openapi.yaml`:

- **`GET /solutions/{id}/landscape` (`getSolutionLandscape`, tag `Solutions`):**
  - `entity:read`;
  - parameters: `q` (string, `maxLength` 255), `metatypeId` (uuid), `PageParam`, `PageSizeParam`, `CursorParam`;
  - 200 with `SolutionLandscapePage`; 400 (limits, `cursor`); 403; 404 (unknown solution).
- **Schemas:**
  - `SolutionLandscapeEntry`: `id` (integer), `name`, `metatypeId`, `metatypeName`, `version`;
  - `SolutionLandscapePage`: `items` (`SolutionLandscapeEntry[]`), `page` (`PageMeta`), `nextCursor` (string or null).

## Risks / Trade-offs

- **[Search with `strpos` cannot use an index, so it scans all active entities]** → the performance test covers a page with search over 10,000 entities with a target of 500 ms. A trigram index follows if the landscape grows beyond that.
- **[The baseline is the state at the time of reading; deltas captured on it can become stale when the catalog changes later]** → the deltas of change 3 store the before value. Detecting such drift belongs to the diff view and the go-live, not to the baseline.
- **[Connections are missing from the baseline]** → deltas of connections are not specified yet. The query adds them when they are.
- **[Cursors do not survive a backend restart]** → as for the catalog: the client repeats the query without a cursor.
