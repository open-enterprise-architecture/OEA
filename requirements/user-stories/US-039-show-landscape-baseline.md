# US-039: Show the Current IT Landscape as the Baseline of a Solution

**ID**: US-039
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As a Solution Architect, I want to see, within a solution, the current state of the IT landscape as a searchable list, so that I know which systems and components already exist and can build my planned changes on this current state.

## Reference

**Use Case**: [UC-05: Describe the Architecture Vision of a Change Initiative](../use-cases/UC-05-describe-architecture-vision.md)
**Persona**: [SH-04: Michael – Solution Architect](../../business-analysis/stakeholders/SH-04-michael-solution-architect.md)
**Requirement**: [REQ-039: Show Current IT Landscape as Solution Baseline](../req/REQ-039-landscape-baseline.md)

## Acceptance Criteria

**AC1**:
- Given: Two implemented solutions: A has `CRM-Legacy` (new), B has `ERP-Core` (new)
- When: Michael opens a new solution and loads the baseline
- Then: `CRM-Legacy` and `ERP-Core` are in the list; no other entities

**AC2**:
- Given: Solution A (implemented) has `CRM-Legacy` (retiring)
- When: Michael loads the baseline
- Then: `CRM-Legacy` is **not** in the list (retiring removes the entity from implemented)

**AC3**:
- Given: Solution A has `ERP-Core` as modified (v4); solution B (more recent, implemented) has `ERP-Core` as modified (v5)
- When: Michael loads the baseline
- Then: the list shows `ERP-Core` with the state from solution B (more recent implementation wins)

**AC4** (empty landscape):
- Given: no implemented solutions present yet
- When: Michael loads the baseline
- Then: the notice "No implemented entities present – you can only create new entities" appears; the `modified` and `retiring` actions are disabled

**AC5** (filter):
- Given: baseline with 20 entities of various types
- When: Michael filters by EntityType `ApplicationComponent`
- Then: only entities of type `ApplicationComponent` are displayed

## Technical Notes

- Affected components: solution detail view (area "Architecture Vision & Scope"), backend `GET /api/v1/solutions/{id}/landscape`
- Aggregation logic (project mode): over all `entity_deltas` from solutions with status=`implemented`; per entityId the most recent implementation wins (ORDER BY `implemented_at DESC`); `retiring` deltas as tombstone (entity is removed from result)
- Database changes: no new tables; query on existing `solutions` + `entity_deltas`
- Pagination: cursor-based (entity lists can become large)
- For MVP: plateau mode (baseline from `fromPlateau`) can initially be mocked with fallback to project mode calculation, if plateau mode is not yet implemented

## Definition of Done

- [ ] Acceptance criteria AC1–AC5 met
- [ ] Code review by a second person
- [ ] Tests: aggregation from multiple solutions (incl. overlaps, retiring as tombstone, latest-wins for modified); empty landscape; filter
- [ ] Performance test: aggregation with 100+ implemented solutions < 500ms
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-038 (solution must exist)
- Blocks: US-040 (retiring/new need baseline), US-041 (modified needs baseline), US-042 (retiring needs baseline), US-043 (diff view builds on baseline)

## Notes

5 SP because of the aggregation logic in the backend (latest-wins, tombstone handling, pagination). The UI is simple (filterable table), but the query logic is not trivial. Separate unit tests for the aggregation function recommended. Plateau mode (baseline from fromPlateau) is specified in REQ-039, but can be implemented in a follow-up story.
