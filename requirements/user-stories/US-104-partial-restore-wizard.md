# US-104: Partial Restore via a Three-Step Wizard

**ID**: US-104
**Story Points**: 8
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to restore an entity selectively via a three-step wizard, so that I can reset only specific fields without overwriting correct changes.

## Reference

**Use Case**: [UC-16](../use-cases/UC-16-partial-entity-restore.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-104](../req/REQ-104-partial-restore-wizard.md)

## Acceptance Criteria

**AC1** (Source version prefilled):
- When: I start the wizard from the timeline for version v3
- Then: the source version v3 is prefilled

**AC2** (Automatic skipping):
- When: step 1 contains no differences
- Then: it is skipped automatically with a note; the wizard moves to step 2

**AC3** (Confirm button disabled without selection):
- When: no fields have been selected in any step
- Then: the confirm button is disabled

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
