# US-044: Show Conflict Warning for Parallel EntityDeltas

**ID**: US-044
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

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
- Then: the warning is still visible on the affected delta (not a one-time toast)

**AC4** (warning disappears at go-live):

- Given: Solution A has a conflicting delta for `CRM-Legacy`; solution B shows a warning for it
- When: Solution A is set to `implemented`
- Then: the warning in solution B disappears on the next load (on the fly, no stored flag)

**AC5** (multiple conflicts):

- Given: Solutions A and B each have a delta for `CRM-Legacy`
- When: Michael creates a delta for `CRM-Legacy` in solution C
- Then: two warnings appear (one per conflicting solution)

## Technical Notes

- Affected components: backend `POST /api/v1/solutions/{id}/deltas` (conflict check after every write), solution detail view (warning display on delta entries)
- Conflict query: `SELECT s.id, s.name, p.display_name FROM entity_deltas ed JOIN solutions s ON ed.solution_id = s.id JOIN persons p ON s.owner_id = p.id WHERE ed.entity_id = :entityId AND s.id != :currentSolutionId AND s.status NOT IN ('implemented', 'archived')`
- Compute the warning on the fly (when loading the delta list), do not store it as a persisted field – this way it is always up to date
- Warning is non-blocking: the delta is saved in any case, even with a conflict
- Response on POST: in addition to the saved delta, optionally a `conflicts: [{ solutionId, solutionName, ownerName }]` array

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
