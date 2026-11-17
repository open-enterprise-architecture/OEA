# Proposal

## Why

Change 3 (`us-084-plateaus`) brought plateaus and solutions that target them, but no plateau can go live yet. A realized plateau, which US-089 already protects, cannot arise. UC-11 needs the go-live of a plateau: the target becomes the new baseline, the old baseline is realized, and the planned changes reach the catalog.

This change brings:

- the go-live with its confirmation by name ([US-086](../../../requirements/user-stories/US-086-go-live-confirmation.md));
- the warnings that do not block it ([US-087](../../../requirements/user-stories/US-087-go-live-no-enforcement.md));
- the atomic transition ([US-088](../../../requirements/user-stories/US-088-go-live-atomic-transition.md));
- the solutions of the plateau going live with it ([US-157](../../../requirements/user-stories/US-157-plateau-solutions-go-live.md), new).

This is S14 change 4, the last change of S14.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-11](../../../requirements/use-cases/UC-11-define-plateau-and-go-live.md) (basic flow, E2, E3, E4); [UC-05](../../../requirements/use-cases/UC-05-describe-architecture-vision.md) (go-live of solutions) |
| User Story | [US-086](../../../requirements/user-stories/US-086-go-live-confirmation.md) (AC1–AC3), [US-087](../../../requirements/user-stories/US-087-go-live-no-enforcement.md) (AC1, AC3; AC2 deferred), [US-088](../../../requirements/user-stories/US-088-go-live-atomic-transition.md) (AC1, AC2), [US-157: Take the Solutions of a Plateau Live with It](../../../requirements/user-stories/US-157-plateau-solutions-go-live.md) (new, AC1–AC4), [US-152](../../../requirements/user-stories/US-152-solution-go-live.md) |
| Requirements | [REQ-086](../../../requirements/req/REQ-086-go-live-two-step-confirmation.md), [REQ-087](../../../requirements/req/REQ-087-go-live-no-enforcement.md), [REQ-088](../../../requirements/req/REQ-088-go-live-atomic-transition.md), [REQ-041](../../../requirements/req/REQ-041-diff-view.md) |
| ADRs | [ADR-017](../../../adrs/ADR-017-architecture-layer-strategy.md), [ADR-018](../../../adrs/ADR-018-business-rule-engine.md) (CEL rules, deferred), [ADR-032](../../../adrs/ADR-032-authorization-model.md) (`plateau:go-live`), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) |
| Sprint / Milestone | S14 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-17:**

- **Solutions go live with their plateau.** The go-live of a plateau P1 first takes every active solution of P1 live, as US-152 does for a single one: deltas into the catalog, the merge dialog for field conflicts, and open issues. Then P1 becomes the baseline and the old baseline P0 is realized. All of it runs in one transaction.
- **Only the direct successor goes live.** Only a plateau whose predecessor is the current baseline can go live. Plateaus that also succeeded the old baseline succeed the new baseline afterwards. Their basis stays.
- **CEL rules are deferred.** There is no rule engine yet (ADR-018), so US-087 AC2 follows with it. The confirmation warns about solutions that are not yet approved, and the preview shows the field conflicts and the open issues the go-live will leave.

**Assumptions recorded here (minor details, open for review):**

- **A solution of a plateau goes live only with its plateau.** Its own go-live gives 409 `plateau-solution` (US-157 AC3). This keeps the content of a plateau consistent: the solutions of later plateaus are built on it.
- **Order of the solutions:** they go live in the order of the last change of their deltas, oldest first, then by name. A field that an earlier solution of the plateau changes, and that a later one changes from another before value, is a field conflict of the later delta. Its current value is the value of the earlier solution.
- **Confirmation by name:** the request carries the name of the plateau. A name that is not exactly the name of the plateau gives 422 naming `name`, and nothing changes (US-086). The dialog enables "Confirm go-live" only for the exact name.
- **Warnings (US-087 AC1, AC3):**
  - the active solutions that go live, and among them the ones in status `draft` or `proposed` ("N solutions not yet approved");
  - the open issues the go-live will leave.

  Without them, the dialog shows no warning section. The field conflicts are not warnings: they stop the go-live until they are resolved, as for a single solution.
- **Effects of the transition (US-088 AC2):**
  - P1 becomes `baseline` with `validFrom` set to the day of the go-live (UTC);
  - P0 becomes `realized` with `validTo` set to the same day, and is read-only from then on;
  - a delta `retiring` soft-deletes its entity, as for a single solution, which is what `retired` means in the catalog;
  - other plateaus that succeeded P0 now succeed P1, and their solutions start from P1 (`fromPlateauId`).
- **Rollback (US-088 AC1):** any failure rolls the whole transition back, including the solutions already taken live in it. The test forces the failure with a database trigger.
- **Permission:** `plateau:go-live` reads the preview and runs the go-live. It does not check `solution:write` or the write roles of values, as the solution go-live does not (owner decision 2026-11-16).
- **Audit:** `plateau.went-live` with P0, P1, the solutions and the counts. Each solution writes its own `solution.went-live`, and each plateau that now succeeds P1 writes `plateau.updated`.
- **Diff of plateau solutions:** the diff (SCR-062) of a solution that targets a plateau compares against the baseline of the solution, the basis of its plateau, instead of the catalog. This is the alignment that change 3 left to this change. With only the direct successor going live, the conflicts of the go-live are computed against the catalog, because the basis of such a plateau is the catalog.

**Plateau go-live (`solution`):**

- `previewPlateauGoLive` (`GET /plateaus/{id}/go-live`) returns P0, P1, the counts summed over the solutions, the solutions with their status and counts, the field conflicts per solution, the open issues the go-live leaves, and the warnings.
- `goLivePlateau` (`POST /plateaus/{id}/go-live`, body `{name}`) answers with the new baseline, the realized plateau, the solutions and their open issues.
  - A plateau that is not the direct successor of the baseline gives 409 `plateau-not-next`.
  - Unresolved field conflicts give 409 `merge-required`, listing the conflicts with their solution.
  - A blocked entity gives 409 `entity-blocked`.
- The field conflicts are resolved per delta with `resolveSolutionDeltaConflicts`, as today. For a delta of a plateau solution, the current value is the value at its turn.

**Frontend (SCR-065, SCR-066, SCR-063):**

- On SCR-065, the next plateau shows "Go live" for holders of `plateau:go-live`.
- This opens the dialog SCR-066 "Take plateau live" (new). It shows:
  - the summary "P1 becomes the new baseline; P0 is realized (read-only)";
  - the counts and the solutions;
  - the warnings;
  - the notice "This action cannot be undone";
  - an input for the plateau name.

  "Confirm go-live" is enabled only for the exact name.
- On 409 `merge-required`, the dialog lists the conflicts per solution and opens SCR-063 for each solution.
- After the go-live, the page shows the new baseline, and the old one under "Realized".
- On SCR-061, a solution of a plateau shows no "Go live", but a link to its plateau.

## Out of Scope

- CEL business rule violations (US-087 AC2; ADR-018).
- The dashboard entry of the go-live for the CIO (UC-11 step 11): the dashboards follow in S16–S17. The audit event records the go-live meanwhile.
- The go-live of a plateau that does not succeed the baseline directly.
- Undoing a go-live.
- The plateau roadmap SCR-025 and the plateau diff.

## Capabilities

### New Capabilities

- `solution/plateau-go-live`: the preview, the confirmation, the warnings, the transition with the solutions of the plateau, the effects on plateaus and solutions, the permission, the audit, and the screen SCR-066.

### Modified Capabilities

- `solution/solution-go-live`: a solution of a plateau goes live only with its plateau, and the current value of a field conflict is the value at its turn.
- `solution/solution-diff`: the diff of a plateau solution compares against its baseline.
- `solution/plateaus`: the action "Go live" on SCR-065.

## Impact

- **api/openapi.yaml:**
  - `previewPlateauGoLive` and `goLivePlateau`, with `PlateauGoLivePreview`, `PlateauGoLiveRequest` and `PlateauGoLiveResult`;
  - the problem types `plateau-not-next` and `plateau-solution`;
  - `solutionId` in the conflicts of `merge-required`.
- **backend:**
  - migration `V38__plateau_go_live.sql`, granting `UPDATE` on `plateaus (status, valid_from, valid_to)` and on `solutions (from_plateau_id)`;
  - `PlateauStore` with the transition;
  - `app/solution/PlateauGoLive`, which plans the solutions in order, reuses `SolutionGoLive` per solution in one transaction, and computes the field conflicts at each turn;
  - `SolutionGoLive` refusing a solution of a plateau;
  - `SolutionDiff` reading the basis;
  - the audit event `plateau.went-live`.
- **frontend:**
  - `PlateauGoLiveDialog.vue` (SCR-066) on `PlateausPage.vue`;
  - `DeltaMergeDialog.vue` for the conflicts of a plateau;
  - `SolutionPage.vue` with the link to the plateau;
  - `api/plateaus.ts`;
  - the stories of SCR-065, SCR-066 and SCR-061.
- **Documentation:**
  - US-157 (new, written with this proposal);
  - US-086, US-087, US-088 and UC-11, realigned, together with REQ-086, REQ-087 and REQ-088;
  - `docs/audit-event-catalog.md`;
  - `business-objects/plateau.md` and `business-objects/solution.md`;
  - `docs/screens/SCREENS.md` (SCR-066; additions to SCR-065 and SCR-061);
  - the user manual for plateaus and solutions in English and German;
  - the S14 row of the sprint plan.
