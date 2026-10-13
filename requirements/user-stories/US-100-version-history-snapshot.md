# US-100: Retrieve the Complete Historical Entity State

**ID**: US-100
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

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

- When: a DELETE request is sent to `/entity-versions/{id}`
- Then: the API responds with HTTP 405

**AC3** (Edit rejected):

- When: a PUT request is sent to `/entity-versions/{id}`
- Then: the API responds with HTTP 405

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
