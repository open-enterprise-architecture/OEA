# US-107: Create an ABB and Build a Refinement Hierarchy

**ID**: US-107
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to create and edit `ArchitectureBuildingBlock` entries and link them in a refinement hierarchy, so that I can build and manage an organization-specific continuum library.

## Reference

**Use Case**: [UC-17](../use-cases/UC-17-manage-continuum-building-blocks.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-107](../req/REQ-107-create-refine-abb.md)

## Acceptance Criteria

**AC1** (Create ABB):

- When: I create an ABB with `continuumLevel=organization` and `maturityLevel=established`
- Then: the system saves it with HTTP 201; it is immediately visible in the library

**AC2** (Cycle detection):

- When: I set ABB "B" to `refines=A` and then want to set ABB "A" to `refines=B`
- Then: the system rejects saving with HTTP 422 "Cycle detected: A → B → A"

**AC3** (Mandatory field industry):

- When: I save an ABB with `continuumLevel=industry` without the `industry` field
- Then: I receive HTTP 422

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
