# US-098: View an Entity's Change History as a Timeline

**ID**: US-098
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to view the complete change history of an entity chronologically and filter it by time period or person, so that I can trace changes and meet compliance requirements.

## Reference

**Use Case**: [UC-14](../use-cases/UC-14-view-change-history.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-098](../req/REQ-098-version-history-timeline.md)

## Acceptance Criteria

**AC1** (Timeline with versions):

- When: I open an entity with 5 versions
- Then: I see 5 entries in the timeline, newest first

**AC2** (Filter by actor):

- When: I filter the timeline by actor "Michael"
- Then: only versions by Michael are displayed

**AC3** (Entity without history):

- When: I open an entity without change history
- Then: the tab shows "No changes since creation. Created on [date] by [person]."

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
