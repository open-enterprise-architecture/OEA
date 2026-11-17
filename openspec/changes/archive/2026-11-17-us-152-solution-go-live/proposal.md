# Proposal

## Why

Since S11, solutions capture deltas `new`, `modified` and `retiring`, but nothing takes them into the repository. In project mode, the current state of the landscape is meant to be the sum of the implemented solutions ([architecture](../../../business-objects/architecture.md), operating modes). Without a go-live, every captured change stays a plan.

[US-152](../../../requirements/user-stories/US-152-solution-go-live.md) (new) adds the go-live of a solution in project mode:

- the deltas become entities, changes and soft deletions in one transaction;
- a real field conflict with a later change is merged by hand;
- metamodel violations do not block and are recorded as open issues.

This is S14 change 1. The plateau mode of [UC-11](../../../requirements/use-cases/UC-11-define-plateau-and-go-live.md) follows in changes 3 and 4.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-05](../../../requirements/use-cases/UC-05-describe-architecture-vision.md) (project mode, go-live of the deltas); [UC-11](../../../requirements/use-cases/UC-11-define-plateau-and-go-live.md) for the plateau go-live |
| User Story | [US-152: Go Live with a Solution in Project Mode](../../../requirements/user-stories/US-152-solution-go-live.md) (new, AC1–AC5) |
| Requirements | [REQ-040](../../../requirements/req/REQ-040-capture-entity-deltas.md), [REQ-162](../../../requirements/req/REQ-162-property-write-protection-all-paths.md), [REQ-103](../../../requirements/req/REQ-103-restore-atomic-transaction.md) (atomic writes) |
| ADRs | [ADR-019: Soft delete](../../../adrs/ADR-019-soft-delete-strategy.md), [ADR-022](../../../adrs/ADR-022-structured-property-model.md), [ADR-028](../../../adrs/ADR-028-backend-layered-architecture.md) |
| Issues | [#69](https://github.com/open-enterprise-architecture/OEA/issues/69) (concurrent edits: answered by the field conflict merge), [#263](https://github.com/open-enterprise-architecture/OEA/issues/263) (go-live question) |
| Sprint / Milestone | S14 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-16:**

- **S14 cut:**
  1. this change;
  2. the open issues after a go-live: blocking, resolved in a merge dialog (US-153, written with change 2);
  3. plateaus (US-084, US-085, US-089, US-038 AC3/AC4);
  4. the plateau go-live (US-086 to US-088).

  US-110 and US-134 move out of S14. The approval workflow (proposed → approved) gets its own use case after S14.
- **Who:** every holder of `solution:write` may take a solution live and edit deltas of solutions owned by others. The values of the deltas are taken as captured. The go-live does not check the write roles of the confirming person again. This answers the go-live question of #263.
- **Retiring:** a delta `retiring` soft-deletes its entity (ADR-019). The entity leaves catalog and baseline, and its history stays.
- **Field conflicts:** a merge opens only for a real field conflict, that is, a field the delta changes that was also changed in the entity after the capture. Per field, the person takes the current value, the delta value or an own value. The go-live continues when all conflicts are resolved, and the resolution is recorded in the delta. Changes to other fields are kept without a merge.
- **Metamodel violations:** they do not block the go-live. Each one is recorded as an open issue of the solution. Change 2 makes open issues blocking and resolvable.
- **Confirmation:** a simple dialog with the counts and the notice that the go-live cannot be undone.
- **Status:** the go-live is possible from every status except `implemented` and `archived`.

**Go-live (`solution`):**

- **`previewSolutionGoLive`** (`GET /solutions/{id}/go-live`, `solution:write`) returns:
  - the counts of new, modified and retired entities, including the connections retired with their endpoint;
  - the field conflicts, each with delta, entity, field, the recorded before value, the current value and the delta value;
  - the metamodel violations that would become open issues.

  Values the caller may not read are redacted, as in the deltas.
- **`resolveSolutionDeltaConflicts`** (`POST /solutions/{id}/deltas/{deltaId}/resolution`) resolves the conflicts of a delta `modified`.
  - Per field, the choice is `current`, `delta` or `value` with a value.
  - The delta then records the current value as its before value and the chosen value as its after value.
  - `current` removes the field from the delta.
  - A field without a conflict is answered with 422 naming the field.
- **`goLiveSolution`** (`POST /solutions/{id}/go-live`):
  - Unresolved field conflicts give 409 `merge-required` with the conflicts, and nothing changes.
  - Otherwise, in one transaction:
    - every delta `new` creates its entity and records its ID;
    - every delta `modified` writes its after values;
    - every delta `retiring` soft-deletes its entity and the connections of that entity;
    - the metamodel violations are stored as open issues;
    - the solution gets the status `implemented`;
    - one audit event `solution.went-live` is written with the counts, and the usual `entity.created`, `entity.updated` and the new `entity.deleted` per entity.
  - A solution in status `implemented` or `archived` gives 409.

**Frontend (SCR-061):**

- The page "Solution" gets "Go live" for holders of `solution:write`, with a confirmation dialog (counts, notice).
- When conflicts exist, the action opens the merge dialog SCR-063 (new) first. Per conflict it shows the field, the before value, the current value and the delta value, with the three choices.
- After the go-live, the page shows the status `implemented`, the deltas without write actions, and the open issues as a read-only list. The actions to resolve them come with change 2.

## Out of Scope

- Making open issues blocking and resolving them (S14 change 2, US-153).
- Plateaus and the plateau go-live (changes 3 and 4).
- The approval workflow, and restoring a soft-deleted entity (#70).
- `If-Match` on deltas, tree nodes and catalogs (#257). The go-live detects concurrent changes by the field conflicts instead.

## Capabilities

### New Capabilities

- `solution/solution-go-live`:
  - the preview, the field conflicts and their resolution;
  - the go-live transaction with soft deletion and open issues;
  - the audit;
  - the action on SCR-061 and the merge dialog SCR-063.

### Modified Capabilities

- None. The rule "Completed solution" of `solution/solution-deltas` already rejects delta changes after the go-live.

## Impact

- **api/openapi.yaml:**
  - `previewSolutionGoLive`, `goLiveSolution` and `resolveSolutionDeltaConflicts`;
  - the schemas `SolutionGoLivePreview`, `DeltaConflict`, `DeltaConflictResolution`, `SolutionGoLiveResult` and `SolutionOpenIssue`;
  - `openIssueCount` in `SolutionDetail`.
- **backend:**
  - migration `V35__solution_go_live.sql`:
    - the table `solution_open_issues`;
    - `UPDATE` on `solutions.status` and `entity_deltas.entity_id`;
    - the soft-delete columns for the runtime role;
  - `app/solution/SolutionGoLive`, which uses `EntityWriter` and `EntityValidator`;
  - `EntityStore.softDelete`;
  - the audit events `solution.went-live` and `entity.deleted`.
- **frontend:**
  - `SolutionPage.vue` with the action, the dialog and the list;
  - the new `DeltaMergeDialog.vue` (SCR-063);
  - `api/solutions.ts`;
  - the stories of SCR-061 and SCR-063.
- **Documentation:**
  - US-152 (new) and UC-05 (go-live in project mode);
  - `docs/audit-event-catalog.md`;
  - `docs/data-model.puml` and `business-objects/solution.md`;
  - `docs/screens/SCREENS.md` (SCR-063);
  - the user manual for solutions in English and German;
  - the S14 row of the sprint plan.
