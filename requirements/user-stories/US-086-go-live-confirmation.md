# US-086: Two-Step Go-Live Confirmation

**ID**: US-086
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to see a confirmation dialog with a diff summary and name input before a go-live transition, so that I can reliably prevent accidental and irreversible transitions.

## Reference

**Use Case**: [UC-11](../use-cases/UC-11-define-plateau-and-go-live.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-086](../req/REQ-086-go-live-two-step-confirmation.md)

## Acceptance Criteria

**AC1** (Button disabled without name input):

- When: I open the go-live dialog without entering a name
- Then: the "Confirm go-live" button is disabled

**AC2** (Wrong name):

- When: I enter a wrong plateau name
- Then: the button stays disabled

**AC3** (Correct name):

- When: I enter the exact plateau name
- Then: the button is enabled and the go-live can be executed

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
