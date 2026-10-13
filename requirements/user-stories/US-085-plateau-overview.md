# US-085: Plateau Overview by Status

**ID**: US-085
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to see a structured overview of all plateaus grouped by status, so that I can see at a glance which state is currently in production and which target states are planned.

## Reference

**Use Case**: [UC-11](../use-cases/UC-11-define-plateau-and-go-live.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-085](../req/REQ-085-plateau-overview.md)

## Acceptance Criteria

**AC1** (Grouped display):
- When: I open the plateau overview
- Then: I see the baseline highlighted, targets with `validFrom`, and realized plateaus in a collapsible section

**AC2** (Empty target section):
- When: no target plateaus exist
- Then: the target section shows "No target plateau planned yet"

## Definition of Done

- [ ] AC1–AC2 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
