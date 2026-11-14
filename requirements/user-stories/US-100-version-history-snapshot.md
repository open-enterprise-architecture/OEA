# US-100: Retrieve the Complete Historical Entity State

**ID**: US-100
**Story Points**: 3
**Status**: done
**Sprint**: S12
**Implementation**: OpenSpec change `us-099-version-diff` – `getEntityVersion` (`GET /entities/{id}/versions/{version}`, `entity:read`): the complete state of a version, reconstructed from the history tables, with the properties mapped at the time including empty ones; `PUT` and `DELETE` on a version answer 405; "Show complete state vN" on SCR-026. Realigned 2026-11-14

## Story

As Kurt – Lead Enterprise Architect, I want to retrieve the complete entity state at any historical version and be certain that this snapshot is immutable, so that I can provide gapless compliance evidence.

## Reference

**Use Case**: [UC-14](../use-cases/UC-14-view-change-history.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-100](../req/REQ-100-version-history-snapshot-retrieval.md)

## Acceptance Criteria

**AC1** (Complete state expandable):

- When: I click "Show complete state v4"
- Then: all fields of the snapshot, including empty properties, are displayed

**AC2** (Delete rejected):

- When: a DELETE request is sent to `/entities/{id}/versions/{version}`
- Then: the API responds with HTTP 405

**AC3** (Edit rejected):

- When: a PUT request is sent to `/entities/{id}/versions/{version}`
- Then: the API responds with HTTP 405

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix

## Notes

- 2026-11-14: implemented by the OpenSpec change `us-099-version-diff` (S12 change 2). No snapshot is stored: the state of a version is reconstructed backwards from the current values and the history tables (ADR-022). Owner decision 2026-11-14: the state lists the properties mapped to the metatype at the time of the version, including empty ones, plus every property that had a value then. AC2 and AC3 realigned from `/entity-versions/{id}` to `/entities/{id}/versions/{version}`.
