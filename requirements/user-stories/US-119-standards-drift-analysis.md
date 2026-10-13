# US-119: Analyze Standards Drift in the Current-State Landscape

**ID**: US-119
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to see all entities that use a prohibited, deprecated, or TRM-deviating SBB, so that I can plan targeted migration measures.

## Reference

**Use Case**: [UC-20](../use-cases/UC-20-analyze-continuum-conformance.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-119](../req/REQ-119-standards-drift-analysis.md)

## Acceptance Criteria

**AC1** (Prohibited entities):

- When: 3 entities have `instanceOfSBBId=prohibited-SBB-UUID`
- Then: all 3 are in the drift list; `prohibited` is highlighted in red

**AC2** (Deviation report):

- When: an entity uses an SBB that does not match the `preferredStandard` of its TRM category
- Then: it appears in the deviation list

**AC3** (Direct link):

- When: I click an entry in the drift list
- Then: the entity detail view opens

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
