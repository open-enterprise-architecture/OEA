# US-114: Execute Package Import Atomically and Handle Conflicts

**ID**: US-114
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want the package import to run as an atomic transaction and to be shown ID conflicts for resolution, so that neither a partial import occurs nor organization-specific building blocks are overwritten unintentionally.

## Reference

**Use Case**: [UC-18](../use-cases/UC-18-import-continuum-package.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-114](../req/REQ-114-atomic-import-conflict-handling.md)

## Acceptance Criteria

**AC1** (Cycle abort):

- When: the imported package contains a cycle in the ABB refinement graph
- Then: the import aborts; no building block is imported; I see an error message with a cycle description

**AC2** (Conflict list):

- When: the package contains 3 building blocks with already existing IDs
- Then: I see the conflict list; the default is "Skip" for all conflicts

**AC3** (Rollback on DB error):

- When: a database error occurs after the import of 20 of 50 building blocks
- Then: a full rollback is executed; 0 building blocks are imported

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
