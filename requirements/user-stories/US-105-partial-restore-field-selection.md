# US-105: Selective Field Selection in the Partial Restore Wizard

**ID**: US-105
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to be able to select in the wizard only fields that actually differ and to see the source value as a preview for each field, so that I can decide precisely what should be restored.

## Reference

**Use Case**: [UC-16](../use-cases/UC-16-partial-entity-restore.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-105](../req/REQ-105-partial-restore-field-selection.md)

## Acceptance Criteria

**AC1** (Identical fields grayed out):
- When: a field is identical in the source and the current state
- Then: the checkbox is grayed out and not clickable

**AC2** (New properties marked):
- When: a property did not yet exist in the source version
- Then: the wizard shows "(not present in source version)"; checkbox not selectable

**AC3** (Preview on field selection):
- When: I activate the checkbox of a changed field
- Then: the source value is displayed as a preview

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
