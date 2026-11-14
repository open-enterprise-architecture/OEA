# US-098: View an Entity's Change History as a Timeline

**ID**: US-098
**Story Points**: 5
**Status**: done
**Sprint**: S12
**Implementation**: OpenSpec change `us-098-entity-history` – `getEntityHistory` (`entity:read`): the versions newest first with the changed fields without values, filters by period and person, paged by cursor; the history visibility per role and metatype (`/admin/history-visibility`, `visibility:configure`); the screen SCR-026 "Change history", opened with the row action "History" of a catalog. Realigned 2026-11-14

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
- Then: the page shows "No changes since creation. Created on [date] by [person]."

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix

## Notes

- 2026-11-14: implemented by the OpenSpec change `us-098-entity-history` (S12 change 1). Owner decisions 2026-11-14: the history is a page of its own, SCR-026, opened from the row action "History" of the catalog and the portal catalog; no change reason; a field the reader may not read never shows a value and appears as "Restricted" or is hidden, configurable per role and metatype, with the more open setting of several roles and "Restricted" as default. AC3 realigned from "the tab" to "the page". Values and the diff of versions follow with US-099 and US-100.
