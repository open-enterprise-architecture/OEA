# US-109: Create an Architecture Pattern

**ID**: US-109
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to create `ArchitecturePattern` entries with a problem description and solution, so that the continuum library contains reusable and complete solution patterns.

## Reference

**Use Case**: [UC-17](../use-cases/UC-17-manage-continuum-building-blocks.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-109](../req/REQ-109-create-architecture-pattern.md)

## Acceptance Criteria

**AC1** (Create pattern):
- When: I create a pattern with `problem` and `solution`
- Then: the system saves it with HTTP 201; it is visible in the library

**AC2** (Mandatory field problem):
- When: I try to save a pattern without the `problem` field
- Then: I receive HTTP 422 "problem is a mandatory field"

**AC3** (Link relatedPatterns):
- When: I link `relatedPatterns` with other patterns via multi-select
- Then: the links are saved and displayed in the detail view

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
