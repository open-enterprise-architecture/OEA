# Design

## Context

- **Plateaus (V37, `us-084-plateaus`):**
  - `plateaus` holds `status`, `succeeds`, `basis`, `valid_from` and `valid_to`.
  - The runtime role may update everything but `status` and `valid_to`.
  - `solutions.from_plateau_id` and `to_plateau_id` reference plateaus; the runtime role has no `UPDATE` on `solutions`.
- **Solution go-live (`app/solution/SolutionGoLive`, us-152):**
  - `plan(solution, lock)` reads the deltas and the stored entities and computes the counts, the field conflicts (the current value against the recorded before value), the metamodel violations and the snapshot values.
  - `goLive` locks the solution, checks the blocks and the conflicts, writes through `EntityWriter`, inserts the open issues, drops the snapshots and sets `implemented`.
  - `resolve` replaces the fields of a delta per choice and records the current value as the before value.
  - SQL runs in `core/solution/GoLiveStore` (ADR-051).
- **Content of a basis (`core/solution/PlateauContentStore`):**
  - It computes the layers of a solution and the overlay of their deltas.
  - The diff (`SolutionDiff`) still reads the catalog.
- **Permissions:**
  - `plateau:go-live` exists, and `enterprise-architect` holds it; no operation uses it yet.
  - Only `enterprise-architect` holds it among the built-in roles.

## Goals / Non-Goals

**Goals:**

- The go-live of the next plateau with its solutions, in one transaction.
- The preview, the confirmation by name, and the warnings.
- The effects on the plateaus and on the solutions of sibling plateaus.
- The refusal of the single go-live of a plateau solution.
- The diff of a plateau solution against its basis.
- The dialog SCR-066 and the action on SCR-065.

**Non-Goals:**

- CEL rules (ADR-018).
- The dashboard entry.
- Going live out of order.
- Undoing a go-live.

## Architecture Constraints

- **SQL only in `core` (ADR-051):** the transition of the plateaus and the order of the solutions are read and written in `core/solution` stores.
- **No package cycles:** `PlateauGoLive` lives in `app/solution` with `SolutionGoLive`.
- **Contract first (ADR-013):** Redocly lint and both authorization scripts pass.

## Decisions

### D1 – Schema (`V38__plateau_go_live.sql`)

- `GRANT UPDATE (status, valid_from, valid_to) ON plateaus TO oea_app`.
- `GRANT UPDATE (from_plateau_id) ON solutions TO oea_app`, for the solutions of sibling plateaus.
- No new table. The go-live is recorded in the audit and in the fields of the plateaus.

### D2 – Order and the value at each turn

- **Order:** `PlateauStore.goLiveOrder(plateauId)` returns the active solutions of the plateau, sorted by the latest `coalesce(updated_at, created_at)` of their deltas (a solution without deltas first), then `lower(name)`, then ID.
- **`plan(solution, lock, ahead)`:** `SolutionGoLive.plan` gets an optional `Ahead` overlay, holding the values that earlier solutions of the go-live set per entity and field, and the entities they retire. `currentValue` reads `ahead` before the stored entity.
  - A delta `modified` of an entity that an earlier solution retires is treated like a deleted entity: 409 `entity-deleted` names the delta.
  - In the real go-live, `ahead` is empty, because the earlier solutions were already written in the same transaction, and the catalog shows their values.
- **Preview:** `PlateauGoLive.preview` plans the solutions in order. After each one it adds the after values of its deltas, and its retired entities, to `ahead`. The preview writes nothing.
- **`resolve` for a plateau solution:** it builds `ahead` from the solutions before it in the order, so that the recorded before value is the value at its turn.

### D3 – `PlateauGoLive`

- **`preview(principal, plateauId)`:**
  - 404 for an unknown plateau, and 409 `plateau-not-next` when its predecessor is not the baseline or it is not a target or transition plateau.
  - It reads the solution plans with `ahead`, as D2 describes.
  - It returns:
    - the old and the new baseline;
    - per solution its name, status, counts and conflicts;
    - the summed counts;
    - the open issues as violations;
    - the warnings `notApproved` (the solutions in `draft` or `proposed`) and `openIssues` (the number of violations and snapshot values).
- **`goLive(principal, plateauId, name)`:**
  1. lock the plateau and the baseline (`FOR UPDATE`), check that it is the next plateau, and check the name (422 naming `name`);
  2. plan every solution against the catalog with its predecessors applied (D2); if any has conflicts, answer 409 `merge-required` with all conflicts, each with `solutionId`, before writing anything;
  3. call `SolutionGoLive.goLive` for each solution in order. It joins the transaction, and a `merge-required` or `entity-blocked` raised there rolls everything back;
  4. `PlateauStore.transition`: P1 becomes `baseline` with `valid_from = current_date` (UTC), P0 becomes `realized` with `valid_to` the same day, the siblings that succeeded P0 now succeed P1, and their solutions start from P1 (`from_plateau_id`);
  5. audit `plateau.went-live`, plus `plateau.updated` naming `succeeds` per sibling.
- The partial unique index on the baseline requires P0 to leave `baseline` before P1 enters it: the store updates P0 first.

### D4 – Refusing the single go-live of a plateau solution

- `SolutionGoLive.goLive` gives `PlateauSolutionException` (409 `plateau-solution`) when the solution has a `to_plateau_id` and is not called from `PlateauGoLive`.
- `PlateauGoLive` calls an internal overload with a flag. `preview` and `resolve` stay open to `solution:write`.

### D5 – Diff of a plateau solution

- `SolutionDiff` reads the current state of an entry from `SolutionLandscape.entry`, through the overlay of the basis, when the solution has layers. Without layers it reads the catalog, as today.

### D6 – Contract (delta to `api/openapi.yaml`)

- **`GET /plateaus/{id}/go-live`** (`previewPlateauGoLive`, `plateau:go-live`):
  - answers `PlateauGoLivePreview`: `plateauId`, `baselineId`, `counts` (`SolutionGoLiveCounts`), `solutions` (`id`, `name`, `status`, `counts`, `conflicts`), `violations`, and `warnings` (`notApproved`: solution names, `openIssues`: number);
  - errors: 403, 404, 409 `plateau-not-next`.
- **`POST /plateaus/{id}/go-live`** (`goLivePlateau`, `plateau:go-live`):
  - takes `PlateauGoLiveRequest {name}` and answers `PlateauGoLiveResult` (`baseline`, `realized`, `solutions` with their open issues);
  - errors: 403, 404, 409 (`plateau-not-next`, `merge-required`, `entity-blocked`, `entity-deleted`), 422 naming `name`.
- **Changed:**
  - `DeltaConflict` gets `solutionId`, optional, set for the conflicts of a plateau go-live;
  - `goLiveSolution` gets 409 `plateau-solution`;
  - the description of `getSolutionDiff` names the basis.

### D7 – Frontend

- **`PlateausPage.vue`:**
  - On the plateau whose `succeeds` is the baseline, it shows "Go live" for holders of `plateau:go-live`.
  - It opens `PlateauGoLiveDialog.vue` (SCR-066), which loads the preview.
- **`PlateauGoLiveDialog.vue` shows:**
  - the summary;
  - the counts;
  - the solutions with their status;
  - the warnings, as a section that is absent without warnings;
  - the notice;
  - the name input.

  "Confirm go-live" is enabled only when the input equals the name exactly.
- **On 409 `merge-required`:**
  - it lists the conflicts per solution;
  - "Resolve" opens `DeltaMergeDialog` for that solution's conflicts;
  - afterwards the preview is read again.
- **After success,** the page reloads, and the dialog closes.
- **`SolutionPage.vue`:** for a solution with `toPlateauId`, it hides "Go live" and shows "Goes live with plateau {name}", linked to `/plateaus`.
- **`api/plateaus.ts`:** `fetchPlateauGoLivePreview` and `goLivePlateau`.

### D8 – Documentation and data model

- `business-objects/plateau.md`: the transition and BR-02, realigned to the go-live with solutions.
- `business-objects/solution.md`: BR-07, a plateau solution goes live with its plateau.
- `docs/data-model.puml`: the grants of V38.
- `docs/audit-event-catalog.md`: `plateau.went-live`.

## Risks / Trade-offs

- **A long transaction:** a plateau with many solutions and deltas holds locks for the whole go-live. Plateau go-lives are rare and deliberate, and the solution go-live has the same shape, so this is accepted. The test measures a go-live of 1,000 deltas and records its time.
- **The overlay of the preview and the real order can differ.** A delta changed between the preview and the go-live changes the order. The go-live therefore plans again under lock (step 2), and the preview is advisory.
- **Refusing single go-lives** changes the behavior of change 1 for plateau solutions only. Project solutions are unaffected.

## Migration Plan

- V38 adds grants only.

## Open Questions

- None. The assumptions of the proposal are open for review in the proposal pull request.
