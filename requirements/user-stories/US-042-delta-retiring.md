# US-042: Mark an Existing Entity in a Solution as Retiring

**ID**: US-042
**Story Points**: 2
**Status**: done
**Sprint**: S11
**Implementation**: OpenSpec change `us-040-entity-deltas` – `createSolutionDelta` (`solution:write`) with `deltaType=retiring` for an entity of the baseline; the action "Retire" in the baseline of SCR-061. Realigned 2026-11-12

## Story

As a Solution Architect, I want to be able to mark an existing entity from the IT landscape in a solution as "being retired" (deltaType=retiring), so that the scope of the initiative clearly shows which systems or components are replaced by this initiative.

## Reference

**Use Case**: [UC-05: Describe the Architecture Vision of a Change Initiative](../use-cases/UC-05-describe-architecture-vision.md)
**Persona**: [SH-04: Michael – Solution Architect](../../business-analysis/stakeholders/SH-04-michael-solution-architect.md)
**Requirement**: [REQ-040: Capture EntityDeltas of a Solution](../req/REQ-040-capture-entity-deltas.md)

## Acceptance Criteria

**AC1**:

- Given: `CRM-Legacy (ApplicationComponent)` is in the baseline
- When: Michael selects `CRM-Legacy` from the baseline and clicks "Retire"
- Then: `CRM-Legacy` appears in the delta list with `deltaType=retiring`; in the diff view (US-043) it is shown as outgoing

**AC2** (not in baseline):

- Given: Michael enters an entityId that is not present in the baseline
- When: he saves
- Then: validation error "Entity not found in the current landscape"

**AC3** (duplicate check):

- Given: `CRM-Legacy` already has a `retiring` delta in this solution
- When: Michael tries to mark `CRM-Legacy` as `retiring` again (a `modified` delta for it follows with US-041)
- Then: validation error "A delta already exists for this entity in this solution"

**AC4** (solution implemented):

- Given: Solution has status=`implemented` or `archived`
- When: Michael tries to mark an entity as retiring
- Then: 409 Conflict "This solution is completed and can no longer be changed"

## Technical Notes

- Affected components: solution detail view (delta capture, action "Retire" directly in the baseline list), backend `POST /api/v1/solutions/{id}/deltas`
- Request body: `{ deltaType: "retiring", entityId: integer }` – minimal payload, no `changes` map needed (entity IDs are integers; realigned 2026-11-12)
- UI: direct action in the baseline list (button or context menu per row); no separate form needed

## Definition of Done

- [x] Acceptance criteria AC1–AC4 met
- [ ] Code review by a second person
- [x] Tests: create retiring, invalid entityId, duplicate check, solution=implemented blocks
- [x] Linter and type checks green
- [x] Entered in trace matrix

## Dependencies

- Waiting on: US-038 (solution), US-039 (baseline must load for the direct action to be possible)
- Blocks: US-043 (retiring deltas are a core component of the diff view)

## Notes

2 SP: minimal backend effort (no `changes` map). UI: direct action in the baseline list is the most natural interaction – Michael sees the entity and marks it with one click. Shared backend endpoint with US-040 and US-041 (`POST /solutions/{id}/deltas`).

- 2026-11-12: implemented by the OpenSpec change `us-040-entity-deltas` (S11 change 2). AC3 realigned: a `new` delta describes a future entity of its own and never one of the baseline, so the duplicate is a second `retiring` delta for the same entity; AC4 also covers `archived` (owner decision 2026-11-12).
