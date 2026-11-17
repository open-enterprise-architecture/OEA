# Design

## Context

- **Solutions (V25):** `solutions.from_plateau_id` and `solutions.to_plateau_id` exist without foreign keys, with the check "both or none" (BR-01). `Solutions.create` always leaves them null.
- **Permissions (V3):** `plateau:go-live` exists in the catalog and in the role `enterprise-architect`. No operation uses it yet.
- **Baseline (`app/solution/SolutionLandscape`):** it pages the catalog with a search and a metatype filter, and it reads one entry with its values. It marks the delta type of each entry in the solution and its parallel changes (`SolutionConflicts`).
- **Deltas (V26, V27):**
  - `entity_deltas` holds `delta_type`, `entity_id` (null for `new`), `metatype_id`, `name`, `description` (for `new`) and `updated_at`.
  - `entity_delta_property_values` holds the after values, and for a delta `modified` also the before values and the system fields `name` and `description`.
  - `SolutionDeltas` records the before values from the catalog.
- **Metamodel question (`app/metamodel/SolutionImpact`, us-153-open-issues D9):**
  - it finds the affected solutions through `core/solution/DeltaValueStore`;
  - it takes `SolutionDecision {solutionId, decision}`, and it answers 409 `solution-decision-required` listing the affected solutions in the extension member `solutions`;
  - `app/metamodel` cannot use `app/solution` (`CycleRules`).
- **Contract:** `/views/roadmap/{id}` (SCR-025) is `planned` with its own plateau band schema. There is no plateau operation yet.

## Goals / Non-Goals

**Goals:**

- Plateaus with their predecessor, basis and read-only protection, with the overview SCR-065.
- Solutions that target a plateau, and their baseline from the basis of the plateau.
- The metamodel question per plateau.

**Non-Goals:**

- The plateau go-live, the lifecycle `retired`, the dashboard entry (change 4).
- The diff and the solution go-live against the basis (change 4).
- The roadmap SCR-025 and the plateau diff.

## Architecture Constraints

- **SQL only in `core` (ADR-051):** the plateau table and the content of a basis are read and written in `core/solution` stores.
- **No package cycles:** `SolutionImpact` (`app/metamodel`) reads the plateau of a solution through `core/solution/DeltaValueStore`, never through `app/solution`.
- **Contract first (ADR-013):** Redocly lint and both authorization scripts pass.
- **Permissions (ADR-032):** `plateau:write` is a non-admin-class permission of the category `plateau`.

## Decisions

### D1 – Schema (`V37__plateaus.sql`)

- The table `plateaus` holds:
  - `id UUID PRIMARY KEY`;
  - `name TEXT NOT NULL CHECK (length BETWEEN 1 AND 255)`, with a unique index on `lower(name)`;
  - `description TEXT CHECK (length <= 2000)`;
  - `status TEXT NOT NULL CHECK (status IN ('baseline', 'transition', 'target', 'realized'))`;
  - `succeeds UUID REFERENCES plateaus(id)`;
  - `basis TEXT CHECK (basis IN ('catalog', 'predecessor'))`;
  - `valid_from DATE` and `valid_to DATE`;
  - `created_at`, `created_by`, `updated_at` and `updated_by`.
- The checks:
  - a partial unique index on `status` where `status = 'baseline'` allows one baseline at most (BR-01);
  - a baseline has neither `succeeds` nor `basis`, and a target or transition has both. A realized plateau keeps what it had.
  - `succeeds <> id`. Longer cycles are checked by the service, because a check constraint cannot follow the chain.
- The foreign keys `solutions.from_plateau_id` and `solutions.to_plateau_id` reference `plateaus(id)`.
- The grants:
  - the runtime role gets `SELECT` and `INSERT` on `plateaus`;
  - it gets `UPDATE` on `name`, `description`, `valid_from`, `succeeds`, `basis`, `updated_at` and `updated_by`. `status` and `valid_to` follow with the go-live of change 4.
- The permission `plateau:write` (category `plateau`, not admin-class) comes before `plateau:go-live` in the sort order, and the role `enterprise-architect` gets it.

### D2 – Store and service

- **`core/solution/PlateauStore`:**
  - `insert`, `update`, `find`, `list` and `baseline`;
  - `chain(id)`, which follows the predecessors with a recursive CTE, for the cycle check and the content;
  - `solutionCount(id)`, the number of solutions with `to_plateau_id = id`.
- **`app/solution/Plateaus`:** `create`, `update`, `get` and `list`, with the checks of the specs in this order:
  1. the status;
  2. the realized plateau (422);
  3. the name;
  4. the predecessor and the cycle;
  5. the basis;
  6. the plateau in use (409).

  It audits `plateau.created` (name, status) and `plateau.updated` (the names of the changed fields). A failed check changes nothing and audits nothing.
- **Errors:**
  - `InvalidPlateauException(field, detail)` gives 422 naming the field. It covers "A baseline plateau already exists", "No baseline plateau exists. Create an initial baseline first." and "Realized plateau is read-only".
  - `PlateauInUseException` gives 409 `plateau-in-use`.

### D3 – Solutions in plateau mode

- `Solutions.NewSolution` gets `fromPlateauId` and `toPlateauId`, and `Solutions.create` checks them in this order:
  1. only one of the two gives 422 "Both plateau references must be set or both empty" (no field);
  2. an unknown target, the baseline as target, or a realized target gives 422 naming `toPlateauId`;
  3. a start other than the predecessor of the target gives 422 naming `fromPlateauId`.
- `PlateauStore.find` reads the target `FOR SHARE`. This keeps a parallel change of its predecessor from slipping between the check and the insert. The update of a predecessor locks the plateau `FOR UPDATE` and counts the solutions afterwards.

### D4 – Content of a basis (`PlateauContent`)

- **Layers:** the basis of a plateau `P` is a list of layers. It is empty when the basis of `P` is `catalog`, or when the predecessor of `P` is the baseline. Otherwise it is the layers of the predecessor followed by the predecessor itself.
  - For a solution, the layers are those of its target plateau. A project solution has none.
- **`core/solution/PlateauContentStore`** builds one CTE `overlay` per query from the active deltas (solution status not `implemented` or `archived`) of the solutions whose target is a layer. Each delta is ranked by the index of its layer, then by `coalesce(updated_at, created_at)`.
  - For each catalog entity, the last ranked value of each field wins: name, description and each property. `retiring` beats `modified` when the retiring delta is ranked later; a later `modified` of the same entity does not revive it.
  - The lifecycle of an entity is `retiring` when a ranked `retiring` delta exists, otherwise `active`. The solution named is the one of the deciding delta.
  - Each delta `new` gives a proposed row with the delta ID, metatype, name and the solution.
- **`SolutionLandscape`:**
  - `read` replaces `FROM entities` by `FROM content` when the solution has layers. `content` is the catalog with the overlay applied, unioned with the proposed rows.
  - Search, metatype filter and the order `(lower(name), key)` run on the overlaid name. A proposed row has no entity ID; its key is `0` followed by the delta ID, so that the cursor stays total.
  - `entry` reads the catalog values and applies the overlay of that entity.
  - Without layers, both run the queries of today.
- **Contract of an entry:**
  - `SolutionLandscapeEntry` gets `lifecycle` (`active`, `retiring` or `proposed`) and `changedBy` (`{solutionId, name}` or null);
  - `id` becomes nullable, null exactly for `proposed`;
  - `deltaId` is set for `proposed`.
- **Performance:** the overlay reads only the deltas of the layers, through the existing indexes on `entity_deltas (solution_id)` and `(entity_id)`. A new index on `solutions (to_plateau_id)` finds the solutions of a layer. The baseline measurement of us-039 is repeated with 50,000 entities and 1,000 deltas in two layers, with the target p95 below 500 ms (`SolutionLandscapeLatencyIT`). Measured on 2026-11-17: p95 68 ms over 20 requests with a search term.

### D5 – Before values and deltas

- `SolutionDeltas` records the before values of a delta `modified` from `SolutionLandscape.entry`, instead of the catalog, so from the overlay for a solution with layers.
- `createSolutionDelta` already takes an entity ID. A proposed row has none, so it cannot be named, and the UI offers no action for it.
- An entity that a layer retires stays in the baseline as `retiring`, and it can still get a delta (spec "Before values from the basis").

### D6 – Metamodel question per plateau

- `DeltaValueStore.solutions` also returns `to_plateau_id` and the name of the plateau. `SolutionImpact.Affected` gets `plateauId` and `plateauName`.
- `SolutionDecision` becomes `{solutionId?, plateauId?, decision}` with exactly one of the two IDs; both or none give 400. The server checks this, because a `oneOf` with `not` makes the Redocly lint warn. In the query, a decision by plateau is `plateau:<plateauId>:apply|keep`. `SolutionImpact` expands each decision by plateau to the affected solutions of that plateau, and then applies `apply` and `keep` as today.
- **Validation:**
  - a decision by solution for a solution with a plateau gives 422 naming the solution;
  - a plateau without affected solutions gives 422 naming the plateau;
  - a missing decision gives 409 as today.
- The audit details of the three metamodel events list the decisions as they were sent, by plateau or by solution.
- Each entry of the extension member `solutions` of the problem `solution-decision-required` gets `plateau: {id, name} | null`.
- `SolutionDecisions.vue` groups the solutions with a plateau under one choice per plateau, naming them, and sends `plateauId`.

### D7 – Contract (delta to `api/openapi.yaml`)

- **Paths:**
  - `GET /plateaus` (`listPlateaus`) and `POST /plateaus` (`createPlateau`, 201);
  - `GET /plateaus/{id}` (`getPlateau`) and `PUT /plateaus/{id}` (`updatePlateau`).
  - Reads use `anyOfPermissions` `plateau:write`, `plateau:go-live` or `solution:write`; writes use `plateau:write`.
  - The responses are 400, 401, 403, 404, 409 (`plateau-in-use`) and 422.
- **Schemas:**
  - `Plateau`: `id`, `name`, `description`, `status`, `succeeds`, `basis`, `validFrom`, `validTo`, `solutionCount`, `createdAt`, `createdBy` and `updatedAt`;
  - `PlateauCreate` and `PlateauUpdate`; `PlateauUpdate` carries `status` only to reject a change;
  - `PlateauBasis` (enum).
- **Changed:**
  - `SolutionCreate` gets `fromPlateauId` and `toPlateauId`; the description of `createSolution` drops "project mode only";
  - `SolutionLandscapeEntry` gets `lifecycle`, `changedBy` and `deltaId`, and `id` becomes nullable;
  - `SolutionDecision` (D6), the pattern of the query parameter `decision`, and `plateau` in the extension member `solutions` of `solution-decision-required`;
  - the permission catalog and the role `enterprise-architect`;
  - the problem type `plateau-in-use`.

### D8 – Frontend

- **`PlateausPage.vue` (SCR-065, route `/plateaus`, navigation entry "Plateaus"):**
  - It is visible with any of the three read permissions, and it loads `listPlateaus`.
  - It has three sections: the baseline as a highlighted card, the target and transition plateaus as a list with `validFrom` and the number of solutions, and "Realized" as a collapsed `UCollapsible`.
  - It shows the empty texts of US-085 AC2 and UC-11 A1.
- **`PlateauDialog.vue`:** create and edit with name, description, `validFrom`, status (on creation), predecessor and basis. The predecessor offers the plateaus that are not realized and preselects the baseline. On edit it is disabled while the plateau has solutions.
- **`SolutionCreateDialog.vue`:** an optional select "Target plateau", shown when plateaus exist, with the start shown read-only.
- **`SolutionPage.vue` and `SolutionBaseline.vue`:** the plateaus in the header, the basis above the baseline, and badges "Retiring by …" and "Proposed by …". A proposed row has no actions.
- **`api/plateaus.ts`**, and fakes in `test/plateaus.ts`.

### D9 – Documentation and data model

- `docs/data-model.puml`: the class `Plateau` with `basis`, `succeeds` and the constraints of D1, and the associations from `Solution`.
- `business-objects/plateau.md` (0.2.0): the attribute `basis`, BR-04 (the predecessor chain is acyclic and does not change while solutions target the plateau), and the content of a plateau.
- `business-objects/solution.md`: BR-01 with the plateau checks.

## Risks / Trade-offs

- **Content computed on read:** a long chain of layers with many deltas makes the baseline slower. The measurement in D4 bounds it. A stored projection is possible later, without a contract change.
- **The diff and the go-live still use the catalog:** before change 4, a solution with the basis `predecessor` shows, in the diff, the drift that its predecessor causes. The go-live of a single solution then offers the merge dialog for those fields. That is correct, but surprising, so the manual names it.
- **Last change wins:** two solutions of one plateau that change the same field are already warned about (us-044). The content shows one of them. The plateau go-live of change 4 decides how they merge.
- **`id` nullable in `SolutionLandscapeEntry`:** the clients of the baseline in this repository are adapted in the same change. There is no external client yet.

## Migration Plan

- V37 adds the table and the foreign keys. Existing solutions have null references, so the keys hold.
- No data migration.

## Open Questions

- None. The assumptions of the proposal are open for review in the proposal pull request.
