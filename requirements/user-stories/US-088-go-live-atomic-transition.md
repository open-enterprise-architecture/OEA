# US-088: Go-Live as Atomic Transition

**ID**: US-088
**Story Points**: 5
**Status**: done
**Sprint**: S14
**Implementation**: OpenSpec change `us-086-plateau-go-live` – `goLivePlateau` in one transaction: the plateau becomes the baseline, the old baseline is realized, retiring entities are soft-deleted; a failure rolls everything back (V38) (AC1, AC2)

## Story

As Kurt – Lead Enterprise Architect, I want to be sure that the go-live transition is either fully executed or fully rolled back, so that no inconsistent architecture state can arise.

## Reference

**Use Case**: [UC-11](../use-cases/UC-11-define-plateau-and-go-live.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-088](../req/REQ-088-go-live-atomic-transition.md)

## Acceptance Criteria

**AC1** (Rollback on error):

- When: a technical error occurs during the transition
- Then: P0 and P1 are unchanged; no partial state is present

**AC2** (Successful go-live):

- When: the go-live is executed successfully
- Then: P1 has `status=baseline`, P0 has `status=realized`, and all `retiring` entities have `lifecycle_state=retired`

## Definition of Done

- [ ] AC1–AC2 fulfilled
- [x] Tests present
- [ ] Code review; linter green; entered in trace matrix
