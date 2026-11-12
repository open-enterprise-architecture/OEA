# Proposal

## Why

A solution architect captures the changes of an initiative against what already exists ([UC-05](../../../requirements/use-cases/UC-05-describe-architecture-vision.md)). Since S10 a solution exists in project mode, but it shows nothing of the current landscape. [US-039](../../../requirements/user-stories/US-039-show-landscape-baseline.md) adds the baseline: the current IT landscape as a searchable list within the solution. It is S11 change 1 and the base of the entity deltas, the diff view and the conflict warning of S11.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-05: Describe the Architecture Vision of a Change Initiative](../../../requirements/use-cases/UC-05-describe-architecture-vision.md) |
| User Stories | [US-039: Show the Current IT Landscape as the Baseline of a Solution](../../../requirements/user-stories/US-039-show-landscape-baseline.md) (AC1–AC5, realigned) |
| Requirements | [REQ-039: Show the current IT landscape as the solution baseline](../../../requirements/req/REQ-039-landscape-baseline.md) |
| ADRs | [ADR-028: Backend layered architecture](../../../adrs/ADR-028-backend-layered-architecture.md), [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md), [ADR-019: Soft delete](../../../adrs/ADR-019-soft-delete-strategy.md), [ADR-031: Screen prototypes](../../../adrs/ADR-031-ui-prototyping-storybook.md) |
| Sprint / Milestone | S11 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-12:**

- **Source of the baseline:** the current catalog, that is the entities that are not deleted. REQ-039 and UC-05 compute it from the deltas of implemented solutions instead. Implemented solutions do not exist before the go-live of S14, so that baseline would stay empty, while the catalog already holds the landscape (batch import, `createEntity`). From S14 on, the go-live writes the deltas of a solution into the catalog, so the catalog stays the current state.
- **Cut of S11** into five changes:
  1. `us-039-solution-baseline` (this change);
  2. `us-040-entity-deltas`: the deltas `new` and `retiring` (US-040, US-042). A `new` delta carries the proposed entity itself, without an entity row until the go-live;
  3. `us-041-modified-delta` (US-041);
  4. `us-043-diff-view` (US-043);
  5. `us-044-conflict-warning` (US-044).
- **US-045** (a new entity in the diagram) moves to the diagrams of S15–S17, because OEA has no canvas yet.
- **#69 and #70** (optimistic locking, external IDs of deleted entities) move to the implementation of `patchEntity` and `deleteEntity`, at the latest with the go-live in S14. Solution deltas write no entities before then.

**Reading the baseline (`getSolutionLandscape`, new):**

- **Who:** `entity:read`. An unknown solution answers 404.
- **Content:** the entities of element metatypes that are not deleted, each with ID, name, metatype ID and name, and version.
- **Search and filter:**
  - `q`: part of the name, case-insensitive, at most 255 characters;
  - `metatypeId`: only entities of this metatype.
- **Order and paging:**
  - by name (case-insensitive), then by ID;
  - `page`, `pageSize` (1 to 200, default 50) and `cursor` like the catalog query, with the total.

**Frontend:** the page "Solution" (SCR-061) gets an area "Baseline":

- the list of the baseline;
- a search field and a metatype filter;
- paging;
- the notice "No entities in the current landscape – you can only create new entities" when the landscape is empty.

**Sprint plan:** the cut of S11 and these decisions.

**Realigned sections:**

- **REQ-039 Statement, Context, Processing and AC1–AC3; US-039 AC1–AC3 and Technical Notes; UC-05 main flow step 8, E2 and the implementation notes:** the baseline in project mode is the current catalog, not an aggregate of implemented solutions. "Retiring removes the entity" becomes "a deleted entity is not listed", and "the most recent implementation wins" becomes "the current version of the entity".
- **US-039 AC4:** the notice reads "No entities in the current landscape – you can only create new entities". The actions `modified` and `retiring` come with changes 2 and 3, which disable them for an empty landscape.
- **US-039 Technical Notes, endpoint:** `GET /api/v1/solutions/{id}/landscape` as `getSolutionLandscape`.
- **US-039 Definition of Done, performance:** "aggregation with 100+ implemented solutions" is replaced by a page of the baseline over 10,000 entities.

## Out of Scope

- **Plateau mode:** the baseline from `fromPlateau` follows with the plateaus in S14.
- **Connections:** the baseline lists the entities of element metatypes. Connections as entries of the baseline follow when deltas of connections are specified.
- **Property values:** the baseline shows no properties. The before values of a `modified` delta are read in change 3 through the entity read, which applies the visibility rules.
- **Deltas:** the actions "Retire" and "Modify" in the list follow in changes 2 and 3.

## Capabilities

### New Capabilities

- `solution/solution-baseline`: the baseline of a solution in project mode: its source, search, metatype filter, order and paging through the API, and the area "Baseline" on the page "Solution".

### Modified Capabilities

None.

## Impact

- **`api/openapi.yaml`:** `getSolutionLandscape` (new, tag `Solutions`) and the schemas `SolutionLandscapeEntry` and `SolutionLandscapePage`.
- **`backend/`:**
  - `app/solution/SolutionLandscape` with the cursors of `CatalogCursors`;
  - `SolutionController` extended;
  - integration, performance and Cucumber tests.
  - No migration.
- **`frontend/`:**
  - the area "Baseline" on SCR-061, with the API client and i18n in English and German;
  - the story SCR-061 extended, with its images.
- **`manual/`:** the page on solutions, in English and German.
- **Documentation:**
  - UC-05, US-039 and REQ-039;
  - `docs/screens/SCREENS.md` (SCR-061);
  - the sprint plan.
