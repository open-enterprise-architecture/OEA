# US-039: Show the Current IT Landscape as the Baseline of a Solution

**ID**: US-039
**Story Points**: 5
**Status**: done
**Sprint**: S11
**Implementation**: OpenSpec change `us-039-solution-baseline` – `getSolutionLandscape` with `entity:read`: the current catalog as the baseline, with search, metatype filter and cursor paging; the area "Baseline" on SCR-061. Realigned 2026-11-12: the baseline in project mode is the current catalog, not the aggregate of implemented solutions

## Story

As a Solution Architect, I want to see, within a solution, the current state of the IT landscape as a searchable list, so that I know which systems and components already exist and can build my planned changes on this current state.

## Reference

**Use Case**: [UC-05: Describe the Architecture Vision of a Change Initiative](../use-cases/UC-05-describe-architecture-vision.md)
**Persona**: [SH-04: Michael – Solution Architect](../../business-analysis/stakeholders/SH-04-michael-solution-architect.md)
**Requirement**: [REQ-039: Show Current IT Landscape as Solution Baseline](../req/REQ-039-landscape-baseline.md)

## Acceptance Criteria

**AC1**:

- Given: The catalog holds the applications `CRM-Legacy` and `ERP-Core` and the data flow "Orders" between them
- When: Michael opens a new solution and loads the baseline
- Then: `CRM-Legacy` and `ERP-Core` are in the list; no other entities (connections are not listed)

**AC2**:

- Given: `CRM-Legacy` is deleted in the catalog
- When: Michael loads the baseline
- Then: `CRM-Legacy` is **not** in the list

**AC3**:

- Given: `ERP-Core` was renamed to `ERP-Core 2` in the catalog, raising its version to 2
- When: Michael loads the baseline
- Then: the list shows `ERP-Core 2` in version 2 (the current state of the entity)

**AC4** (empty landscape):

- Given: the catalog holds no entity yet
- When: Michael loads the baseline
- Then: the notice "No entities in the current landscape – you can only create new entities" appears; the `modified` and `retiring` actions (US-041, US-042) are disabled

**AC5** (filter):

- Given: baseline with 20 entities of various types
- When: Michael filters by EntityType `ApplicationComponent`
- Then: only entities of type `ApplicationComponent` are displayed

## Technical Notes

- Affected components: solution detail view (area "Baseline" on SCR-061), backend `GET /api/v1/solutions/{id}/landscape` (`getSolutionLandscape`)
- Source (project mode, owner decision 2026-11-12): the entities of element metatypes in the catalog that are not deleted; from S14 on, the go-live writes the deltas of a solution into the catalog
- Database changes: none; query on `entities` and `metatypes`
- Pagination: `page`, `pageSize` (1–200) and a cursor, as for the catalog query
- For MVP: plateau mode (baseline from `fromPlateau`) can initially be mocked with fallback to project mode calculation, if plateau mode is not yet implemented

## Definition of Done

- [ ] Acceptance criteria AC1–AC5 met
- [ ] Code review by a second person
- [x] Tests: deleted entities, current state, connections not listed; empty landscape; search and filter; paging by cursor
- [x] Performance test: a page of the baseline with a search term over 10,000 entities < 500 ms (realigned 2026-11-12)
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-038 (solution must exist)
- Blocks: US-040 (retiring/new need baseline), US-041 (modified needs baseline), US-042 (retiring needs baseline), US-043 (diff view builds on baseline)

## Notes

5 SP because of the aggregation logic in the backend (latest-wins, tombstone handling, pagination). The UI is simple (filterable table), but the query logic is not trivial. Separate unit tests for the aggregation function recommended. Plateau mode (baseline from fromPlateau) is specified in REQ-039, but can be implemented in a follow-up story.

- 2026-11-12: implemented by the OpenSpec change `us-039-solution-baseline` (S11 change 1). Realigned (owner decision 2026-11-12): the baseline in project mode is the current catalog, not the aggregate of the deltas of implemented solutions, which do not exist before the go-live (S14); AC1–AC3 and the notice of AC4 follow from it. The actions `modified` and `retiring` follow with US-041 and US-042, which disable them for an empty landscape.
