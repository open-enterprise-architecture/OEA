# US-043: Show the Diff View of a Solution

**ID**: US-043
**Story Points**: 5
**Status**: done
**Sprint**: S11
**Implementation**: OpenSpec change `us-043-diff-view`, commit 8aca0d0 (#250) – `getSolutionDiff` (`entity:read`), paged by cursor with counts per type: per delta the current state of the entity in the catalog next to its target state, with drift since capture; the screen SCR-062 "Solution comparison", linked from SCR-061 with "Compare". Realigned 2026-11-13

## Story

As a Solution Architect, I want to see a diff view that shows me which entities change through this solution and how the target state compares to the current state, so that I can check the scope at a glance and make sure all planned changes are captured correctly.

## Reference

**Use Case**: [UC-05: Describe the Architecture Vision of a Change Initiative](../use-cases/UC-05-describe-architecture-vision.md)
**Persona**: [SH-04: Michael – Solution Architect](../../business-analysis/stakeholders/SH-04-michael-solution-architect.md)
**Requirement**: [REQ-041: Diff View of Current State vs. Target State of a Solution](../req/REQ-041-diff-view.md)

## Acceptance Criteria

**AC1** (retiring):

- Given: Solution has a `retiring` delta for `CRM-Legacy`
- When: Michael opens the diff view
- Then: `CRM-Legacy` appears once; left side shows it in the current state, right side shows it in red/struck through as "out of service"

**AC2** (new):

- Given: Solution has a `new` delta for `Salesforce (ApplicationComponent)`
- When: Michael opens the diff view
- Then: `Salesforce` appears once; left side empty (does not yet exist), right side green as a new entity

**AC3** (modified):

- Given: Solution has a `modified` delta for `ERP-Core` (property "Release": v4 → v5)
- When: Michael opens the diff view
- Then: `ERP-Core` appears once; left "Release" `v4`, right "Release" `v5`; only the changed property is highlighted

**AC4** (no dump of the entire landscape):

- Given: Solution has 3 deltas; the baseline contains 100 entities
- When: Michael opens the diff view
- Then: exactly 3 entries are displayed; all other 97 entities are **not** visible

**AC5** (empty delta list):

- Given: Solution has no deltas yet
- When: Michael opens the diff view
- Then: the notice "No changes captured yet" appears; no error

**AC6** (live update):

- Given: Michael has the diff view open
- When: he saves another delta (in US-040–042) and opens the diff view again, or chooses "Refresh" in it
- Then: the diff view shows the new delta without a full page reload

## Technical Notes

- Affected components: the screen SCR-062 "Solution comparison" at `/solutions/{id}/diff`, linked from SCR-061 with "Compare" (realigned 2026-11-13; not a tab of the solution page), backend `GET /api/v1/solutions/{id}/diff` (`getSolutionDiff`)
- Computed on the fly: baseline (REQ-039 data) + EntityDeltas → diff result; not persisted
- Response structure (realigned 2026-11-13): one page of `entries` in the order of the delta list, each `{ deltaId, deltaType, entityId, metatypeId, metatypeName, name, inCatalog, fields: [{ field, propertyDefId, propertyName, dataType, redacted, current, target, changed, catalogChanged }] }`, with `page`, `counts` per type and `nextCursor`
- UI layout: split panel or before/after cards; visual differentiation:
  - `new`: green background / plus icon; left column empty
  - `retiring`: red background / strikethrough; right column greyed out
  - `modified`: changed properties highlighted in color (yellow or blue); unchanged properties dimmed
- Live update: the diff view loads the diff each time it is opened, and "Refresh" loads it again in place; no WebSocket needed for MVP (realigned 2026-11-13)

## Definition of Done

- [ ] Acceptance criteria AC1–AC6 met
- [ ] Code review by a second person
- [ ] Tests: diff with new/modified/retiring; empty delta list; full entity list stays hidden from diff; live update after delta save
- [ ] Snapshot test for visual differentiation (colors/symbols)
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-039 (baseline backend), US-040 (new deltas), US-041 (modified deltas), US-042 (retiring deltas)
- Blocks: none (diff view is the endpoint of the UC-05 workflow; review workflow follows in a future UC)

## Notes

5 SP because of the split-panel UI and the property-level diff display for `modified` (before/after per property, dimmed unchanged properties). The backend diff is O(n) in the number of deltas and thus unproblematic. The UI is the more laborious part of this story.

- 2026-11-13: implemented by the OpenSpec change `us-043-diff-view`, commit 8aca0d0 (#250) (S11 change 4). Owner decisions 2026-11-13: the left side shows the current catalog and the right side the catalog with the after values applied; a field whose before value recorded at capture differs from the catalog is marked "Changed in the catalog since capture"; the diff view is a screen of its own, SCR-062; a solution holds any number of deltas, and the delta list and the diff are paged and loaded on demand ("Load more"). AC3 realigned (`version` is the lock counter of an entity, not a property), AC6 realigned to the screen of its own.
