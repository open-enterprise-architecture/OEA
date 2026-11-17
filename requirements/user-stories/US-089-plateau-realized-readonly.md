# US-089: Realized Plateau Is Immutable

**ID**: US-089
**Story Points**: 3
**Status**: done
**Sprint**: S14
**Implementation**: OpenSpec change `us-084-plateaus` – `updatePlateau` answers 422 "Realized plateau is read-only", and SCR-065 shows no "Edit" for a realized plateau (AC1, AC2)

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
- [x] Tests present
- [ ] Code review; linter green; entered in trace matrix
