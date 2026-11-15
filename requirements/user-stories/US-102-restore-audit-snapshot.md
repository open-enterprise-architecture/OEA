# US-102: Automatic Audit Snapshot Before Restore

**ID**: US-102
**Story Points**: 3
**Status**: done
**Sprint**: S13
**Implementation**: OpenSpec change `us-101-entity-restore` – the restore writes one version with `restoredFromVersion` and the reason (`entity_version_headers`, V32); the version before it is the snapshot; SCR-026 shows "Restored from vN" and the reason (AC1, AC2; AC3 realigned 2026-11-15: no copy, delta versioning by ADR-022)

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
- Then: the state before the restore exists unchanged as the previous version in the history and can be retrieved as a complete state

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
