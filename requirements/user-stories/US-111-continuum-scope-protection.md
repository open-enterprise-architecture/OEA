# US-111: Protect Imported Continuum Building Blocks from Changes

**ID**: US-111
**Story Points**: 2
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want imported and built-in continuum building blocks to be non-editable and non-deletable, so that the integrity of the originating frameworks and traceability to package updates are preserved.

## Reference

**Use Case**: [UC-17](../use-cases/UC-17-manage-continuum-building-blocks.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-111](../req/REQ-111-continuum-scope-protection.md)

## Acceptance Criteria

**AC1** (API write protection):

- When: a PUT or DELETE request is issued on a building block with `scope=imported`
- Then: the system responds with HTTP 422

**AC2** (UI write protection and badge):

- When: I open a building block with `scope=imported` in the UI
- Then: edit and delete buttons are not visible; a badge "Imported – TOGAF 10 TRM" is visible

**AC3** (SBB assignments editable):

- When: I open a TRM category with `scope=imported`
- Then: I can edit SBB assignments; name and parent category are locked

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
