# Proposal

## Why

Since change 3, a solution holds new, modified and retiring entities. The delta list shows what was captured, but not what the landscape looks like afterwards. With more than a handful of deltas, the architect has to compare each one against the catalog in their head ([UC-05](../../../requirements/use-cases/UC-05-describe-architecture-vision.md)). [US-043](../../../requirements/user-stories/US-043-diff-view.md) adds a diff view: each affected entity once, with its current state from the catalog next to its target state after the solution. It is S11 change 4. The conflict warning of change 5 follows.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-05: Describe the Architecture Vision of a Change Initiative](../../../requirements/use-cases/UC-05-describe-architecture-vision.md) |
| User Stories | [US-043: Show the Diff View of a Solution](../../../requirements/user-stories/US-043-diff-view.md) (AC1–AC6, realigned) |
| Requirements | [REQ-041: Diff view of current state vs. target state of a solution](../../../requirements/req/REQ-041-diff-view.md) (AC1–AC5, realigned) |
| ADRs | [ADR-022: Structured property model](../../../adrs/ADR-022-structured-property-model.md), [ADR-028: Backend layered architecture](../../../adrs/ADR-028-backend-layered-architecture.md), [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md), [ADR-031: Screen prototypes](../../../adrs/ADR-031-ui-prototyping-storybook.md) |
| Sprint / Milestone | S11 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-13:**

- **Current state:** the left side shows the current catalog, as the baseline does. The right side shows the catalog with the after values of the solution applied.
- **Drift:** a delta `modified` recorded its before values at capture. Where such a value differs from the catalog today, the view marks the field as "Changed in the catalog since capture".
- **Screen:** the diff view is a screen of its own, SCR-062 "Solution comparison" at `/solutions/{id}/diff`, linked from the area "Changes" of SCR-061.

**Reading the diff (`getSolutionDiff`, new):**

- `GET /solutions/{id}/diff` with `entity:read`, computed on each request and not stored.
- **Only affected entities:** one entry per delta, in the order of the delta list (`new`, `modified`, `retiring`, each by name). An entity without a delta does not appear. The list is not paged; a solution holds at most 1000 deltas.
- **Entry:** delta ID and type, entity ID (none for `new`), metatype, and a list of fields. Each field has its current value, its target value, whether it changes and whether the catalog changed since capture.
  - `new`: no current state; the target state is the proposed entity with its values.
  - `modified`: current and target state; the changed fields come from the delta, the other fields keep the catalog value on both sides.
  - `retiring`: the current state; no target state, the entity goes out of service.
- **Fields:** name, description and every property with a value on at least one side.
- **Visibility:** a value the reader may not read is redacted on both sides, with the rule of the delta list (`readableFor` per entity).
- **Entity no longer in the catalog:** if the entity of a `modified` or `retiring` delta was deleted after capture, the entry has no current state and is marked "No longer in the catalog".
- **Errors:** an unknown solution answers 404, a caller without `entity:read` 403. A solution without deltas returns an empty list.

**Frontend (SCR-062, new):**

- one card per entry with two columns, current state and target state, stacked on a narrow screen;
- `new`: marked "New", with a plus icon and a green accent; the left column reads "Does not exist yet";
- `modified`: changed fields highlighted with a marker, unchanged ones dimmed; the drift marker with an icon and text;
- `retiring`: marked "Retiring", with a red accent; the right column shows the entity struck through as "Out of service";
- the notice "No changes captured yet" without deltas;
- a summary line with the counts per type, the action "Refresh" and a link back to the solution;
- colors are never the only signal: each marking also carries a label or icon.
- **SCR-061:** the link "Compare" in the area "Changes".

**Realigned sections:**

- **US-043 AC3 and REQ-041 AC2:** `version` is the lock counter of an entity, not a property; the example changes a property "Release" from `v4` to `v5`, as in US-041.
- **US-043 AC6 and REQ-041 AC3:** the diff view is a page of its own. It loads the diff each time it is opened, so returning from the page "Solution" after saving a delta shows it without a full page reload; "Refresh" loads it again in place.
- **US-043 Technical Notes and REQ-041 Implementation Notes:** the response is a list of entries with a field list, not `before`/`after` maps; the diff view is SCR-062, not a tab of the solution page.

## Out of Scope

- **Conflicts with other solutions:** change 5 (US-044).
- **Connections and diagrams:** the diff covers entities only.
- **Review and approval** of a solution based on the diff.
- **Export** of the diff.
- **Resolving drift:** the view marks it; updating the recorded before values belongs to the go-live (S14).

## Capabilities

### New Capabilities

- `solution/solution-diff`: computing the diff of a solution, its visibility rules and the screen SCR-062 with its link from SCR-061.

### Modified Capabilities

None.

## Impact

- **`api/openapi.yaml`:** the new operation `getSolutionDiff` and the schemas `SolutionDiff`, `SolutionDiffEntry` and `SolutionDiffField`.
- **`backend/`:**
  - a new app service `SolutionDiff`, reusing the delta reading of `SolutionDeltas` and the catalog values of `EntityStore`;
  - `SolutionController` implements `getSolutionDiff`;
  - integration and Cucumber tests.
  - No migration and no audit event: the diff is read-only and not stored.
- **`frontend/`:**
  - the page `SolutionDiffPage.vue` with its route, the API client and i18n;
  - the link "Compare" on SCR-061;
  - the story `SCR-062-solution-diff.stories.ts` with its images.
- **`manual/`:** the page on solutions, in English and German.
- **Documentation:**
  - US-043 and REQ-041;
  - `docs/screens/SCREENS.md` (SCR-062);
  - the sprint plan.
  - No data model change: the diff is not persisted.
