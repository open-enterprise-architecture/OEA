# US-106: Log Restored Fields in the Audit Trail

**ID**: US-106
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want the exact restored fields to be visible in the timeline entry after every partial restore, so that the audit trail remains complete and traceable.

## Reference

**Use Case**: [UC-16](../use-cases/UC-16-partial-entity-restore.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-106](../req/REQ-106-partial-restore-restored-fields.md)

## Acceptance Criteria

**AC1** (restoredFields in version entry):

- When: I partially restore `description` and `properties.owner`
- Then: the version entry contains `restoredFields=["description","properties.owner"]`

**AC2** (Reject empty selection):

- When: no fields are selected and I want to confirm
- Then: the button is disabled; an API call with empty `restoredFields` is rejected with HTTP 422

**AC3** (Timeline readability):

- When: I view the timeline after the partial restore
- Then: the entry shows "Partially restored from v4 (description, properties.owner)"

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
