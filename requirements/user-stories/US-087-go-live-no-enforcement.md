# US-087: Execute Go-Live Despite Warnings

**ID**: US-087
**Story Points**: 2
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to be able to execute a go-live even if non-implemented solutions or CEL violations exist, so that I can consciously accept compromises without being blocked by the system.

## Reference

**Use Case**: [UC-11](../use-cases/UC-11-define-plateau-and-go-live.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-087](../req/REQ-087-go-live-no-enforcement.md)

## Acceptance Criteria

**AC1** (Non-implemented solutions as warning):

- When: 3 non-implemented solutions exist at go-live
- Then: the dialog shows "3 solutions not implemented"; go-live remains executable

**AC2** (CEL violations as warning):

- When: CEL violations exist at go-live
- Then: the dialog shows "N business rule violations"; go-live remains executable

**AC3** (No warning without problems):

- When: no warnings exist
- Then: no warning section is displayed in the dialog

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
