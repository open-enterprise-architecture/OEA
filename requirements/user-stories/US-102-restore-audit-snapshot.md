# US-102: Automatic Audit Snapshot Before Restore

**ID**: US-102
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want a snapshot of the current state to be saved automatically before every restore and to be visible in the timeline as a restore entry, so that the audit trail remains gapless.

## Reference

**Use Case**: [UC-15](../use-cases/UC-15-restore-entity-state.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-102](../req/REQ-102-restore-audit-snapshot.md)

## Acceptance Criteria

**AC1** (Snapshot entry visible):

- When: a restore has been executed
- Then: a new timeline entry with `restoredFromVersion=N` is visible

**AC2** (Marker in timeline):

- When: I view the timeline after a restore
- Then: the entry shows "Restored from vN"

**AC3** (Snapshot before restore preserved):

- When: the restore is completed
- Then: the state before the restore exists as an independent snapshot

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
