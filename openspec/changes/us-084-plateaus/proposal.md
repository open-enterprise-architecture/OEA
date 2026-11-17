# Proposal

## Why

OEA supports only the project mode so far. A solution cannot reference plateaus (`fromPlateauId` and `toPlateauId` are always null), and nobody can create a baseline or a target plateau. Organizations that steer their architecture with baseline and target states (UC-11) need these plateaus before the plateau go-live of change 4 can exist.

This change brings:

- plateaus with their overview and their read-only protection ([US-084](../../../requirements/user-stories/US-084-create-plateau.md), [US-085](../../../requirements/user-stories/US-085-plateau-overview.md), [US-089](../../../requirements/user-stories/US-089-plateau-realized-readonly.md));
- solutions that work toward a plateau ([US-038](../../../requirements/user-stories/US-038-create-solution.md) AC3, AC4);
- the basis of a plateau ([US-155](../../../requirements/user-stories/US-155-plateau-basis.md), new);
- the metamodel question of US-154, asked per plateau ([US-156](../../../requirements/user-stories/US-156-metamodel-change-per-plateau.md), new).

This is S14 change 3.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-11](../../../requirements/use-cases/UC-11-define-plateau-and-go-live.md) (A1, A2, A3, the overview, BR-03); [UC-05](../../../requirements/use-cases/UC-05-describe-architecture-vision.md) (solutions in plateau mode); [UC-04](../../../requirements/use-cases/UC-04-configure-metamodel.md) (metamodel changes) |
| User Story | [US-084](../../../requirements/user-stories/US-084-create-plateau.md) (AC1–AC3), [US-085](../../../requirements/user-stories/US-085-plateau-overview.md) (AC1, AC2), [US-089](../../../requirements/user-stories/US-089-plateau-realized-readonly.md) (AC1, AC2), [US-038](../../../requirements/user-stories/US-038-create-solution.md) (AC3, AC4), [US-155: Choose the Basis of a Plateau](../../../requirements/user-stories/US-155-plateau-basis.md) (new, AC1–AC4), [US-156: Decide a Metamodel Change per Plateau](../../../requirements/user-stories/US-156-metamodel-change-per-plateau.md) (new, AC1–AC3) |
| Requirements | [REQ-084](../../../requirements/req/REQ-084-create-plateau.md), [REQ-085](../../../requirements/req/REQ-085-plateau-overview.md), [REQ-089](../../../requirements/req/REQ-089-plateau-realized-read-only.md), [REQ-038](../../../requirements/req/REQ-038-create-solution.md) (BR-01), [REQ-039](../../../requirements/req/REQ-039-landscape-baseline.md), [REQ-037](../../../requirements/req/REQ-037-architecture-metamodel-extension.md) |
| ADRs | [ADR-017](../../../adrs/ADR-017-architecture-layer-strategy.md) (operating modes), [ADR-032](../../../adrs/ADR-032-authorization-model.md) (permissions), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) (SQL only in `core`) |
| Sprint / Milestone | S14 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-17:**

- **Plateau mode is implicit:** there is no instance setting. Creating the first baseline plateau turns plateau mode on. From then on, a solution may reference plateaus, both or none (BR-01). Solutions without plateaus keep working as in project mode.
- **Permission:** the new permission `plateau:write` creates and edits plateaus; the role `enterprise-architect` gets it. `plateau:go-live` stays separate for change 4.
- **Basis of a plateau:** a target or transition plateau is based either on the current catalog or on the content of its predecessor plateau. The basis is chosen when the plateau is created, and it can be changed as long as the plateau is not realized.
  - The content of a plateau is its basis with the deltas of the active solutions that target it.
  - A solution that targets a plateau sees the basis of that plateau as its baseline.
- **Metamodel question per plateau:** the question of US-154 groups the affected solutions by their target plateau. One decision (`apply` or `keep`) applies to all affected active solutions of that plateau. A solution without a plateau is still asked on its own.

**Assumptions recorded here (minor details, open for review):**

- **Plateau fields:**
  - the name is mandatory, 1 to 255 characters, and unique case-insensitively;
  - the description is optional and has at most 2000 characters;
  - `validFrom` is an optional date.
- **Status rules:**
  - the status is `baseline`, `target` or `transition` at creation, and it cannot be changed by an update; only the go-live of change 4 changes it.
  - a second baseline gives 422 "A baseline plateau already exists" (US-084 AC3).
  - a baseline has no predecessor and no basis; its content is the current catalog.
- **Predecessor (`succeeds`):**
  - a target or transition plateau needs a predecessor; without a baseline it gives 422 "No baseline plateau exists. Create an initial baseline first." (UC-11 E1);
  - the predecessor is any plateau that is not realized, and the chain of predecessors has no cycle (422 naming `succeeds`);
  - the predecessor cannot be changed while a solution targets the plateau (409 `plateau-in-use`), because the solutions start from it.
- **Updates:** `updatePlateau` changes name, description, `validFrom`, the predecessor and the basis. For a realized plateau, it gives 422 "Realized plateau is read-only" (US-089 AC1). Realized plateaus only come with change 4; the tests set one up directly.
- **Reading:** the plateaus can be read with `plateau:write`, `plateau:go-live` or `solution:write`, so that solution architects can pick a plateau.
- **Solutions in plateau mode:**
  - `toPlateauId` is a target or transition plateau that is not realized;
  - `fromPlateauId` is the predecessor of the target plateau, and any other value gives 422 naming `fromPlateauId`;
  - only one of the two gives 422 "Both plateau references must be set or both empty" (US-038 AC4);
  - the plateau references of a solution cannot be changed after its creation.
- **Content of a plateau with the basis "predecessor":**
  - It is computed on each read. It is the content of the predecessor with the deltas of the active solutions that target the predecessor, recursively up to the baseline or to a plateau with the basis "catalog".
  - A delta `modified` applies its after values, and a delta `retiring` marks its entity as retiring.
  - A delta `new` lists its proposed entity. A proposed entity cannot get a delta of its own until it exists, after its go-live.
  - When two active solutions of the same plateau change the same field, the delta changed last wins. The entry also carries the conflict warnings that change `us-044-conflict-warning` already computes.
- **Before values:** a delta `modified` records its before values from the baseline of its solution, so from the content of the basis.
- **Unchanged in this change:** the diff (SCR-062) and the go-live of a single solution still compare against the catalog. The plateau go-live of change 4 aligns them with the basis.
- **Metamodel decisions:**
  - A decision names either a solution or a plateau.
  - A decision by solution for a solution that targets a plateau gives 422 naming the solution (US-156 AC3).
  - The 409 `solution-decision-required` lists each affected solution with its plateau.
- **Audit:** `plateau.created` and `plateau.updated` with the changed fields.

**Plateaus (`solution`):**

- `listPlateaus`, `createPlateau`, `getPlateau` and `updatePlateau` under `/plateaus`.
- A plateau returns its fields, its status, its predecessor, its basis, `validTo`, the number of solutions that target it, and the creator with the time of creation.

**Solutions:**

- `createSolution` takes `fromPlateauId` and `toPlateauId`.
- `getSolutionLandscape` and `getSolutionLandscapeEntity` read the basis of the target plateau. An entry gets the lifecycle `retiring` or `proposed` from a predecessor solution, with that solution named.

**Frontend:**

- **SCR-065 "Plateaus" (new) at `/plateaus`:**
  - the baseline, highlighted;
  - the target and transition plateaus, with `validFrom`, or the text "No target plateau planned yet" (US-085 AC2);
  - the realized plateaus in a collapsible section;
  - "Create initial baseline" with its notice when no baseline exists (UC-11 A1);
  - "New plateau" and "Edit" for holders of `plateau:write`, but no "Edit" for a realized plateau (US-089 AC2).
- **SCR-060:** the dialog "New solution" offers the target plateau when plateaus exist, and shows its predecessor as the start.
- **SCR-061:** the header names the plateaus, and the area "Baseline" names the basis and marks retiring and proposed entries.
- **SCR-030:** the decision list of the metamodel dialogs groups the solutions by plateau.

## Out of Scope

- The plateau go-live, the lifecycle states `retired` and the dashboard entry (change 4: US-086, US-087, US-088).
- The diff and the solution go-live against the basis (change 4).
- The plateau roadmap SCR-025 and the plateau diff (concept §11.5).
- Deleting a plateau (UC-11 out of scope).
- Changing the plateau references of an existing solution.

## Capabilities

### New Capabilities

- `solution/plateaus`: creating, reading, editing and the read-only protection of plateaus, the permission `plateau:write`, the basis and the content of a plateau, the audit, and the screen SCR-065.

### Modified Capabilities

- `solution/solution-creation`: the plateau references of a solution and their checks.
- `solution/solution-baseline`: the baseline of a solution that targets a plateau, with the lifecycle of its entries.
- `solution/solution-deltas`: the before values from the basis, and no delta for a proposed entity of a predecessor.
- `metamodel/property-mappings`: the question per plateau, and the decisions by plateau.

## Impact

- **api/openapi.yaml:**
  - the four plateau operations with `Plateau`, `PlateauWrite` and `PlateauBasis`;
  - the permission `plateau:write` in the catalog and in the role `enterprise-architect`;
  - `fromPlateauId` and `toPlateauId` in `SolutionCreate`;
  - `lifecycle` and `proposedBy` in the baseline entries;
  - `plateauId` in `SolutionDecision` (one of `solutionId` or `plateauId`), and the plateau of each affected solution in the problem `solution-decision-required`;
  - the problem type `plateau-in-use`.
- **backend:**
  - migration `V37__plateaus.sql`:
    - the table `plateaus` with one baseline at most, the checks of predecessor and basis, and the column grants;
    - the foreign keys from `solutions.from_plateau_id` and `solutions.to_plateau_id`;
    - the permission `plateau:write`;
  - `core/solution/PlateauStore` (ADR-051);
  - `app/solution/Plateaus`, and `app/solution/PlateauContent` for the content of a basis;
  - `Solutions.create` with the plateau references, and `SolutionLandscape` and `SolutionDeltas` reading the basis;
  - `SolutionImpact` with the decisions by plateau;
  - the audit events `plateau.created` and `plateau.updated`.
- **frontend:**
  - `PlateausPage.vue` (SCR-065) with `PlateauDialog.vue`, its route and its navigation entry;
  - `SolutionCreateDialog.vue` with the plateau;
  - `SolutionPage.vue` and `SolutionBaseline.vue` with the plateaus and the lifecycle;
  - `SolutionDecisions.vue` grouped by plateau;
  - `api/plateaus.ts`;
  - the stories of SCR-065, SCR-060, SCR-061 and SCR-030.
- **Documentation:**
  - US-155 and US-156 (new, written with this proposal);
  - US-084, US-085, US-089 and US-038 AC3, AC4;
  - REQ-084, REQ-085, REQ-089 and UC-11, realigned;
  - `docs/audit-event-catalog.md`;
  - `docs/data-model.puml`, `business-objects/plateau.md` (`basis`, BR-04 for the predecessor chain) and `business-objects/solution.md` (BR-01);
  - `docs/screens/SCREENS.md` (SCR-065; additions to SCR-060, SCR-061 and SCR-030);
  - the user manual for plateaus, solutions and the metamodel, in English and German;
  - the S14 row of the sprint plan.
