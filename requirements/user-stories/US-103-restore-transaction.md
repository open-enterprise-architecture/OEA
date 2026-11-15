# US-103: Restore Is Atomic and Rollback-Safe

**ID**: US-103
**Story Points**: 3
**Status**: done
**Sprint**: S13
**Implementation**: OpenSpec change `us-101-entity-restore` – entity update, version entry and audit event `entity.restored` in one transaction, rolled back as a whole on an error (AC1 realigned 2026-11-15, AC2)

## Story

As Kurt – Lead Enterprise Architect, I want to be sure that a restore is either fully executed or fully rolled back, so that no inconsistent state without an audit snapshot can arise.

## Reference

**Use Case**: [UC-15](../use-cases/UC-15-restore-entity-state.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-103](../req/REQ-103-restore-atomic-transaction.md)

## Acceptance Criteria

**AC1** (Rollback on error):

- When: a technical error occurs after the entity was written
- Then: the entity is unchanged; no new version and no audit event exist

**AC2** (Successful restore):

- When: the restore is successful
- Then: snapshot, entity update, and version entry are all present and consistent

## Definition of Done

- [ ] AC1–AC2 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
