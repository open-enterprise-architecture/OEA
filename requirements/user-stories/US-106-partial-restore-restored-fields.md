# US-106: Log Restored Fields in the Audit Trail

**ID**: US-106
**Story Points**: 3
**Status**: done
**Sprint**: S13
**Implementation**: OpenSpec change `us-104-partial-restore`, commit bc72b2e (#287) – `restoreEntity` with `fields` records `restoredFields` (V33) and names them in `entity.restored`; an empty or unknown selection gives 422; the timeline shows "Partially restored from vN" with the labels (AC1–AC3; AC1 and AC3 realigned 2026-11-15: field keys and labels instead of dot notation)

## Story

As Kurt – Lead Enterprise Architect, I want the exact restored fields to be visible in the timeline entry after every partial restore, so that the audit trail remains complete and traceable.

## Reference

**Use Case**: [UC-16](../use-cases/UC-16-partial-entity-restore.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-106](../req/REQ-106-partial-restore-restored-fields.md)

## Acceptance Criteria

**AC1** (restoredFields in version entry):

- When: I partially restore `description` and the property `owner`
- Then: the version entry contains `restoredFields` with `description` and the ID of `owner`

**AC2** (Reject empty selection):

- When: no fields are selected and I want to confirm
- Then: the button is disabled; an API call with empty `restoredFields` is rejected with HTTP 422

**AC3** (Timeline readability):

- When: I view the timeline after the partial restore
- Then: the entry shows "Partially restored from v4 (Description, Owner)"

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
