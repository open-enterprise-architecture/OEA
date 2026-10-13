# US-089: Realized Plateau Is Immutable

**ID**: US-089
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want realized plateaus to no longer be editable, so that the architecture history remains immutable and auditable.

## Reference

**Use Case**: [UC-11](../use-cases/UC-11-define-plateau-and-go-live.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-089](../req/REQ-089-plateau-realized-read-only.md)

## Acceptance Criteria

**AC1** (API write protection):
- When: I send a PUT request for a realized plateau
- Then: I receive HTTP 422 "Realized plateau is read-only"

**AC2** (UI write protection):
- When: I view a realized plateau in the overview
- Then: no edit button is visible

## Definition of Done

- [ ] AC1–AC2 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
