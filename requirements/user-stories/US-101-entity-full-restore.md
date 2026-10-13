# US-101: Fully Restore an Entity to an Earlier State

**ID**: US-101
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to reset an entity to any earlier state and see a confirmation dialog with a diff while doing so, so that I can reliably and traceably undo erroneous changes.

## Reference

**Use Case**: [UC-15](../use-cases/UC-15-restore-entity-state.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-101](../req/REQ-101-entity-full-restore.md)

## Acceptance Criteria

**AC1** (Restore to earlier state):
- When: I restore an entity to version v3
- Then: `name`, `description`, and `properties` are set to v3 values; `id` is unchanged

**AC2** (Confirmation dialog with diff):
- When: I initiate the restore
- Then: the dialog shows which fields differ between the current state and the target version

**AC3** (Cancel without change):
- When: I cancel the dialog
- Then: the entity remains unchanged

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
