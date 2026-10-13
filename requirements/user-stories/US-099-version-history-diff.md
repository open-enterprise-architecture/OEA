# US-099: Show Field-by-Field Diff Between Versions

**ID**: US-099
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

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

**AC2** (Dot notation for properties):
- When: `properties.owner` was changed
- Then: the entry appears as "properties.owner: 'Kurt' → 'Michael'"

**AC3** (Cumulative diff):
- When: I select versions v2 and v7 for comparison
- Then: I see the cumulative diff across all transitions from v2 to v7

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
