# Design

## Context

See proposal.md – Why. The current state after change 3 (`us-041-modified-delta`, #247):

- **Tables:** `entity_deltas` holds the deltas of a solution; `SolutionDeltas.MAX_DELTAS` limits them to 1000 (change 2), which this change removes. `entity_delta_property_values` holds per field the after value (`new`, `modified`) and the recorded before value (`modified`); a row is a property (`property_def_id`) or a system field (`system_field`).
- **`SolutionDeltas.list`:** returns all deltas in one response, in the order `new`, `modified`, `retiring`, each by name. A `new` delta carries its values (`Value`), a `modified` delta its changes (`Change`, with recorded before and after values). Redaction uses `PropertyAccess.Snapshot.readableFor(property, entityId)` for `modified` and `readable(property)` for `new`.
- **`SolutionLandscape`:** pages the baseline by cursor with `CatalogCursors` (fingerprint of the query, page number and the key values of the last row). `entry` reads one entity of the baseline with its readable values. The baseline condition is "not deleted and not a connection".
- **`EntityStore.values(entityId)`:** the typed values of one entity.
- **Frontend:** `SolutionPage.vue` (SCR-061) with the areas "Changes" (`SolutionDeltas.vue`) and "Baseline"; it derives the baseline markers (`deltaTypes`) from the complete delta list. The router has `/solutions/:id`.
- **Contract:** no diff operation exists yet, planned or implemented.

## Goals / Non-Goals

**Goals:**

- One read that returns, for each delta, the full current and target state of its entity, so the screen does not combine several endpoints.
- The same redaction as the delta list.
- Any number of deltas per solution, with every list of deltas paged in the API and loaded on demand in the screens (owner decision 2026-11-13).

**Non-Goals:**

- No persistence, no migration and no audit event.
- No change to how deltas are captured.

## Architecture Constraints

- **ADR-028:** `SolutionDiff` lives in `app/solution` and returns app records; the controller maps them, and no `core` type reaches `api`.
- **ADR-022:** values stay typed; the existing `SolutionDeltaValue` carries them.
- **ADR-032:** `getSolutionDiff` declares `entity:read`, as `listSolutionDeltas` and `getSolutionLandscape` do.
- **ADR-031:** SCR-062 is a story in `frontend/src/screens/` with generated images.
- **REQ-071:** paging by cursor, as the baseline and the catalog do.

## Decisions

### D0 – No limit, paged delta list

- **Limit:** `MAX_DELTAS` and its count query go; the 422 `deltas` is removed from the contract, the code and the tests. No migration: the limit was never a database constraint.
- **Paging of `listSolutionDeltas`:** `page` (≥ 1, default 1), `pageSize` (1–200, default 50) and `cursor`, as `getSolutionLandscape`. The order gets the delta ID as a tie-breaker: `type rank, lower(name), id`, where the name of a `modified` or `retiring` delta is the current name of its entity. The cursor carries these three key values; its fingerprint holds the solution ID, so a cursor of another solution answers 400 `cursor`.
- **Response:** `SolutionDeltaList` keeps `items` and gains `page` (`PageMeta`, total over all types), `counts` (`SolutionDeltaCounts`: `new`, `modified`, `retiring`) and `nextCursor`. A client that reads only `items` sees the first page.
- **Counts:** one `GROUP BY delta_type` query per request; the screens show them without loading every page.
- **Baseline markers:** `SolutionLandscapeEntry` gains `deltaType` (`modified`, `retiring` or null), read with a `LEFT JOIN entity_deltas ON entity_id = e.id AND solution_id = ?` in the baseline query and in `entry`. `SolutionPage.vue` drops its `deltaTypes` map and passes the entry's value to `SolutionBaseline.vue`.
- **Order stability:** a rename of an entity between two page loads can move its delta across the cursor; it then appears twice or not at all until the list is loaded again. Accepted, as for the baseline.

**Alternative considered:** keeping the full list and only raising the limit. Rejected by the owner decision: there is no upper bound.

### D1 – Contract

```yaml
/solutions/{id}/diff:
  get:
    operationId: getSolutionDiff
    x-oea-authorization: {"access": "permission", "permissions": ["entity:read"]}
    parameters: page, pageSize (1–200, default 50), cursor   # as listSolutionDeltas
    responses: 200 SolutionDiff, 400, 401, 403, 404, 429

SolutionDiff:
  entries: SolutionDiffEntry[]          # one page, in the order of the delta list
  page: PageMeta                        # total over all pages
  counts: SolutionDeltaCounts           # new, modified, retiring over all pages
  nextCursor: string | null

SolutionDiffEntry:
  deltaId: uuid
  deltaType: SolutionDeltaType
  entityId: integer | null              # null for new
  metatypeId: uuid
  metatypeName: string
  name: string                          # target name, or current name for retiring
  inCatalog: boolean                    # false for new and for a deleted entity
  fields: SolutionDiffField[]

SolutionDiffField:
  field: name | description | property
  propertyDefId, propertyName, dataType  # for property
  redacted: boolean
  current: SolutionDeltaValue | null     # null: no value, or no current state
  target: SolutionDeltaValue | null      # null: no value, or no target state
  changed: boolean
  catalogChanged: boolean                # drift of a modified field
```

- **No current or target state:** expressed by the entry type and `inCatalog`, not by an extra flag per field. For `new` and for an entity no longer in the catalog, every `current` is null; for `retiring`, every `target` is null and `changed` is true.
- **Why one entry per delta, not per entity:** a solution holds one delta per entity, so the two coincide; the delta ID lets the screen link back.
- **Alternative considered:** `before`/`after` maps as in the Technical Notes of US-043. Rejected: a map cannot express a redacted value or a drift mark per field, and the field order would be lost.

### D2 – Computation (`SolutionDiff.read`)

1. Check the solution (404) and take the `PropertyAccess` snapshot of the reader.
2. Read one page of deltas with the paged `SolutionDeltas.list` (D0), with the same parameters and cursor; it already applies the order and the redaction, and returns the counts and `nextCursor`.
3. Read the catalog state of the page's entities in two queries in `SolutionDiff`: the entities of the baseline (`id = ANY(?)`, not deleted, not a connection) and their values with property name, data type and reference target, redacted with `readableFor`. At most 200 entities per page, so one round trip each. (Changed during the implementation: an `EntityStore.values` overload would return bare typed values without names and reference targets.)
4. Per delta, build the fields:
   - **`new`:** name, description and each value of the delta; `current` null, `changed` true.
   - **`modified`:** name and description, then every property with a value in the catalog or in the delta, ordered by property name. A field of the delta has `target` = its after value and `changed` = true; any other field has `target` = `current` and `changed` = false. `catalogChanged` is true when the recorded before value differs from the current value. Not in the catalog: `current` null, `target` the after values and, for other fields, null.
   - **`retiring`:** the catalog fields; `target` null, `changed` true.
5. Redaction: a property field is redacted when `readableFor(property, entityId)` is false (`readable(property)` for `new`), with `current`, `target` and `catalogChanged` empty. A reference value carries the ID and the name of the referenced entity, as in the delta list.

- **Why reuse `SolutionDeltas.list`:** the order, the paging, the redaction rule and the value mapping stay in one place, so the diff cannot disagree with the delta list. A cursor of the delta list is also valid for the diff of the same solution, since both page the same rows.
- **Comparing values:** `TypedValue` equality, as `computeChanges` uses for "changes nothing".

### D3 – Screen SCR-062

- **Route:** `/solutions/:id/diff`, name `solution-diff`, component `SolutionDiffPage.vue`. It loads the solution name (`getSolution`) and the first page of the diff on mount; "Load more" appends the next page with `nextCursor`; "Refresh" loads the first page again.
- **Layout:** a header with the solution name, a link back, the counts per type (from `counts`) and "Refresh"; "Load more" below the last card while `nextCursor` is set; one card per entry with the entity name, the metatype and a type badge, and a two-column grid of fields (`md:grid-cols-2`, one column below).
- **Markings:**
  - `new`: badge "New" (`success`, icon plus); the current column reads "Does not exist yet".
  - `retiring`: badge "Retiring" (`error`, icon minus); the target column reads "Out of service", with the values struck through (`line-through`) and dimmed.
  - `modified`: badge "Modified" (`warning`, icon pencil); changed fields with a highlighted background and the label "Changed" for screen readers; unchanged fields dimmed (`text-muted`).
  - drift: icon alert plus "Changed in the catalog since capture"; no longer in the catalog: "No longer in the catalog" in the current column.
  - hidden: "Hidden", as in the delta list.
- **Accessibility:** each column has a heading ("Current state", "Target state"); markings never depend on color alone; the story test runs axe.
- **SCR-061:** `SolutionDeltas.vue` gets the link "Compare" (a `RouterLink`) in its header, shown when deltas exist, the total, and "Load more" below the list while `nextCursor` is set. `SolutionPage.vue` loads the first page after each capture, edit or removal, and the baseline page it shows, so the markers follow.

### D4 – Tests

- **Backend:** `SolutionDeltasTests` for the removed limit (the 1001st delta is accepted) and the paging; `SolutionLandscapeTests` for `deltaType`; `SolutionDiffTests` (integration, Testcontainers) for the computation, redaction, drift, the deleted entity, the empty diff, paging, 404 and 403; Cucumber in `solution-deltas.feature`, `solution-baseline.feature` and the new `solution-diff.feature`.
- **Frontend:** `SolutionDiffPage.spec.ts` for markings, the empty notice, "Load more", refresh and errors; `SolutionPage.spec.ts` for "Load more" and the markers from `deltaType`; the story `SCR-062-solution-diff.stories.ts` with a play function; the fake API pages its deltas and returns a diff computed from them.

## Risks / Trade-offs

- **No limit** → a solution can grow without bound. Paging bounds each response to 200 entries; the counts query and the keyset paging stay cheap with the existing index `entity_deltas_solution` on `solution_id`; the sort by entity name happens within one solution. A very long list in the browser grows with each "Load more"; a virtualized list can follow if needed.
- **Changed response of `listSolutionDeltas`** → a client that expected all deltas in `items` now gets the first page. The only client is the frontend of this repository, changed in the same change.
- **Drift only for recorded fields** → a property that the delta does not change but that changed in the catalog is not marked; it simply shows the catalog value on both sides, which is correct for the target state.
- **Deleted entity of a `retiring` delta** → it has no current state and is marked, but the delta stays; removing it is the architect's choice.

## Migration Plan

None: no schema change. Rollback removes the operation and the screen, and restores the limit and the unpaged list.

## Data Model

- `docs/data-model.puml`: the note "at most 1000 per solution (application rule)" on `entity_deltas` is removed.
- `business-objects/solution.md`: BR-06 without "at most 1000 deltas", with a change history entry.
- The diff itself is computed and not stored.

## Open Questions

None.
