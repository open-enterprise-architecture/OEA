# US-099: Show Field-by-Field Diff Between Versions

**ID**: US-099
**Story Points**: 5
**Status**: done
**Sprint**: S12
**Implementation**: OpenSpec change `us-099-version-diff` – `compareEntityVersions` (`GET /entities/{id}/diff`, `entity:read`): the net diff of the states of two versions, field by field with old and new value; the diff of a version against its predecessor on SCR-026, and the compare mode. Realigned 2026-11-14

## Story

As Kurt – Lead Enterprise Architect, I want to see a field-by-field diff when clicking a version and to compare any two versions directly, so that I can understand changes quickly without technical background knowledge.

## Reference

**Use Case**: [UC-14](../use-cases/UC-14-view-change-history.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-099](../req/REQ-099-version-history-diff-view.md)

## Acceptance Criteria

**AC1** (Changed fields only):

- When: I click a version
- Then: I see only the changed fields; identical fields are hidden

**AC2** (Each property a field of its own):

- When: the property `owner` was changed
- Then: the entry appears as "Owner: Kurt → Michael", with the label of the property

**AC3** (Comparison of two versions):

- When: I select versions v2 and v7 for comparison
- Then: I see the net diff of the state of v2 and the state of v7; a field changed and changed back between them does not appear

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix

## Notes

- 2026-11-14: implemented by the OpenSpec change `us-099-version-diff` (S12 change 2). Owner decisions 2026-11-14: fields are named by their label instead of the dot notation (AC2 realigned); the comparison of two versions is the net diff of their states (AC3 realigned); the diff, the complete state and the compare mode are shown inline on SCR-026.
