# US-084: Create a Plateau

**ID**: US-084
**Story Points**: 5
**Status**: done
**Sprint**: S14
**Implementation**: OpenSpec change `us-084-plateaus` – `createPlateau` with `plateau:write` (role `enterprise-architect`), one baseline, a predecessor and a basis for target and transition plateaus, `plateau.created` (V37); the dialog on SCR-065 (AC1–AC3)

## Story

As Kurt – Lead Enterprise Architect, I want to create plateaus with status `baseline`, `target`, or `transition` and specify a name, description, and a predecessor reference, so that I can structure strategic architecture states in the EA repository.

## Reference

**Use Case**: [UC-11](../use-cases/UC-11-define-plateau-and-go-live.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-084](../req/REQ-084-create-plateau.md)

## Acceptance Criteria

**AC1** (Create baseline):

- When: I create a plateau without `succeeds` with `status=baseline`
- Then: the API responds with HTTP 201 and `status=baseline`

**AC2** (Create target):

- When: I create a plateau with `status=target` and `succeeds`=baseline ID
- Then: HTTP 201 is returned; `succeeds` is set correctly

**AC3** (Reject second baseline):

- When: I try to create another plateau with `status=baseline`
- Then: I receive HTTP 422 with the message "A baseline plateau already exists"

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [x] Tests present
- [ ] Code review; linter green; entered in trace matrix
