# US-110: Compose a Reference Architecture from Building Blocks

**ID**: US-110
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to compose `ReferenceArchitecture` entries from ABBs and patterns and approve them as a blueprint for plateaus, so that reusable architecture guidelines speed up planning work.

## Reference

**Use Case**: [UC-17](../use-cases/UC-17-manage-continuum-building-blocks.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-110](../req/REQ-110-compose-reference-architecture.md)

## Acceptance Criteria

**AC1** (Reference architecture with blueprint approval):
- When: I create a reference architecture with 3 ABBs, 1 pattern, and `governanceStatus=approved`
- Then: the system saves it with HTTP 201; it is selectable as a blueprint in UC-11 (plateau creation)

**AC2** (Empty composition warning):
- When: I try to save a reference architecture without ABBs and without patterns
- Then: the system shows a governance warning; saving is not blocked

**AC3** (Mandatory field targetIndustry):
- When: I save a reference architecture with `continuumLevel=industry` without `targetIndustry`
- Then: I receive HTTP 422

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
