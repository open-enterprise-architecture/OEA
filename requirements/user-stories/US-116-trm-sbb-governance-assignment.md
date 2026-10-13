# US-116: Maintain SBB Governance Assignment per TRM Category

**ID**: US-116
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to set a `preferredStandard`, `acceptedAlternatives`, and `deprecatedOptions` (SBBs) per TRM category, so that the TRM has real steering effect on the current-state landscape.

## Reference

**Use Case**: [UC-19](../use-cases/UC-19-configure-trm.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-116](../req/REQ-116-trm-sbb-governance-assignment.md)

## Acceptance Criteria

**AC1** (preferredStandard and deviation note):
- When: I set `preferredStandard=PostgreSQL 17` in the category "Database Management"
- Then: all entities in this category with a different SBB show the note "Deviation from TRM standard"

**AC2** (Mutually exclusive):
- When: I set the same SBB in `preferredStandard` and `acceptedAlternatives` simultaneously
- Then: I receive HTTP 422 "SBB may appear in only one list"

**AC3** (Imported category editable):
- When: I open a TRM category with `scope=imported`
- Then: I can edit SBB assignments; name and parent category are locked

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
