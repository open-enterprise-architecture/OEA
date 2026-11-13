# US-044: Show Conflict Warning for Parallel EntityDeltas

**ID**: US-044
**Story Points**: 3
**Status**: done
**Sprint**: S11
**Implementation**: OpenSpec change `us-044-conflict-warning`, commit 1e8ebe4 (#253) – `conflicts` on every delta of `createSolutionDelta`, `updateSolutionDelta`, `listSolutionDeltas` (with `conflicted`) and `getSolutionDiff`, `parallelChanges` on the baseline entries, computed on each read; the warnings on SCR-061 and SCR-062; the setting per metatype `updateMetatypeParallelChanges` on SCR-030. Realigned 2026-11-13

## Story

As a Solution Architect, I want to see a warning when another active solution also changes the same entity, so that I know early where coordination with other architects is needed, before the conflict becomes expensive at go-live.

## Reference

**Use Case**: [UC-05: Describe the Architecture Vision of a Change Initiative](../use-cases/UC-05-describe-architecture-vision.md)
**Persona**: [SH-04: Michael – Solution Architect](../../business-analysis/stakeholders/SH-04-michael-solution-architect.md)
**Requirement**: [REQ-042: Conflict Warning for Parallel EntityDeltas](../req/REQ-042-conflict-warning-parallel-solutions.md)

## Acceptance Criteria

**AC1** (conflict detected):

- Given: Solution A (draft, owner: Maria) contains a delta for `CRM-Legacy`
- When: Michael saves a delta for `CRM-Legacy` in solution B
- Then: a warning appears "This entity is also being changed in 'Solution A' (owner: Maria). Please coordinate."

**AC2** (no conflict with implemented):

- Given: Solution A (implemented) contains a delta for `CRM-Legacy`
- When: Michael saves a delta for `CRM-Legacy` in solution B
- Then: **no** warning (implemented is not an active conflict)

**AC3** (persistent warning):

- Given: the warning for `CRM-Legacy` was displayed when saving the delta
- When: Michael reloads solution B
- Then: the warning is still visible on the affected delta (not a one-time toast), because it is computed on each read of the delta list

**AC4** (warning disappears at go-live):

- Given: Solution A has a conflicting delta for `CRM-Legacy`; solution B shows a warning for it
- When: Solution A is set to `implemented`
- Then: the warning in solution B disappears on the next load (on the fly, no stored flag)
- Note: no operation changes the status of a solution before S12–S14; until then the tests set the status of solution A directly in the database

**AC5** (multiple conflicts):

- Given: Solutions A and B each have a delta for `CRM-Legacy`
- When: Michael creates a delta for `CRM-Legacy` in solution C
- Then: two warnings appear (one per conflicting solution)

## Technical Notes

- Affected components: every read of a delta – `POST` and `PUT /api/v1/solutions/{id}/deltas`, the delta list and the diff – and the baseline; SCR-061 (entry, header, notice after saving, baseline marker) and SCR-062 (card)
- Conflict query (realigned 2026-11-13): one query per page for the entities of the page, `entity_deltas` of other solutions joined with `solutions` and the owner in `persons`, `status NOT IN ('implemented', 'archived')`; only deltas `modified` and `retiring` have an entity, so `new` deltas never conflict (owner decision 2026-11-13)
- Compute the warning on the fly (when loading the delta list), do not store it as a persisted field – this way it is always up to date
- Warning is non-blocking: the delta is saved in any case, even with a conflict
- Response (realigned 2026-11-13): `conflicts: [{ solutionId, solutionName, status, ownerId, ownerName, deltaType }]` on every delta and diff entry, `conflicted` on the delta list, `parallelChanges` on a baseline entry when its metatype shows parallel changes (setting per metatype, default off, owner decision 2026-11-13)

## Definition of Done

- [ ] Acceptance criteria AC1–AC5 met
- [ ] Code review by a second person
- [ ] Tests: conflict with draft/proposed/in-progress solution; no conflict with implemented/archived; multiple conflicts; warning persists; warning disappears after go-live
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-040 (new delta endpoint), US-041 (modified delta endpoint), US-042 (retiring delta endpoint) – all share the same backend endpoint that triggers the conflict check
- Blocks: none

## Notes

3 SP: The actual logic is a simple SQL join; the effort lies in the UI (persistent warning display on the delta, not just as a toast) and in the tests (various solution status combinations). Important: compute the warning on the fly, do not store it as a flag, so that it disappears automatically when the conflict resolves (AC4).

- 2026-11-13: implemented by the OpenSpec change `us-044-conflict-warning`, commit 1e8ebe4 (#253) (S11 change 5). Owner decisions 2026-11-13: a conflict is a delta `modified` or `retiring` on the same entity in another active solution; two `new` deltas never conflict; the warning shows in the response of saving, on the delta list, in the header of SCR-061 and on the cards of SCR-062; the baseline marks entities changed by other active solutions for metatypes whose setting "Show parallel changes in the baseline" is on (default off, `metamodel:write`). AC3 and AC4 realigned, Technical Notes realigned to the arrays on the delta, the diff entry and the baseline entry.
