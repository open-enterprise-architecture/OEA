# US-103: Restore Is Atomic and Rollback-Safe

**ID**: US-103
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to be sure that a restore is either fully executed or fully rolled back, so that no inconsistent state without an audit snapshot can arise.

## Reference

**Use Case**: [UC-15](../use-cases/UC-15-restore-entity-state.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-103](../req/REQ-103-restore-atomic-transaction.md)

## Acceptance Criteria

**AC1** (Rollback on error):

- When: a technical error occurs after the snapshot is written
- Then: the entity is unchanged; no snapshot exists in `entity_versions`

**AC2** (Successful restore):

- When: the restore is successful
- Then: snapshot, entity update, and version entry are all present and consistent

## Definition of Done

- [ ] AC1–AC2 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
