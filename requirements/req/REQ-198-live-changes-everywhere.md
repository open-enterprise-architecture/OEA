---
id: REQ-198
title: Live display of changes in every view
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-10-28
last_updated: 2026-10-28
author: requirements-engineer
references:
  use_cases:
    - UC-24
  business_objects:
    - diagram
    - entity
    - catalog
    - dashboard
  business_rules: []
  stakeholders:
    - SH-03
    - SH-04
    - SH-01
    - SH-08
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-047
supersedes: []
superseded_by: []
---

# REQ-198: Live display of changes in every view

## Statement

The system SHOULD show a change made by one participant to every other participant who has an affected diagram, document, entity form, catalog or dashboard open, without the participant reloading the view.

## Rationale

Architects who work in parallel on the same landscape need to see the current state; stale views lead to decisions on outdated information and to conflicting edits (UC-24 step 4, A2).

## Context

Diagrams and documents receive changes through the collaboration session (REQ-199); forms, catalogs and dashboards receive committed changes through the event stream (§21.5) and update the affected rows, fields or figures.

## Type-Specific Fields

### For type=functional

**Trigger**: A change of an entity, connection, diagram, note, comment, catalog or dashboard is made

**Input**: the change (created, changed, deleted object)

**Output / Behavior**: every open view that shows the object updates; changed elements are highlighted briefly in the colour of the author; a catalog adds, updates or removes rows that match its query; a dashboard recalculates affected figures

**Business rules**: a field a participant is currently editing is not overwritten by an incoming change; the participant sees a notice and can take over the new value or keep editing

## Acceptance Criteria

**AC1** (Diagram):

- Given: Kurt and Michael have the same diagram open
- When: Michael adds an interface
- Then: Kurt sees the interface appear without reloading, highlighted in Michael's colour

**AC2** (Catalog):

- Given: Franz has the catalog "Application landscape" open
- When: Kurt creates an application component that matches the catalog query
- Then: the new row appears in Franz's catalog without reloading

**AC3** (Form field being edited):

- Given: Kurt edits the description of an entity in a form without having saved
- When: Michael saves another description of the same entity
- Then: Kurt's input is kept, and Kurt sees a notice with Michael's new value

**AC4** (Dashboard):

- Given: Anna has a dashboard open that counts applications by status
- When: an application changes its status
- Then: the figures of the dashboard update without reloading

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: two or more browser sessions of different persons against the same instance (Playwright with several browser contexts)
- [x] Measuring tool: Playwright, backend integration tests
- [x] Pass criterion: all acceptance criteria green
- [ ] Integrated in CI: planned with the implementing change

## Dependencies

- **Prerequisites**: event stream (§21.5, issue #26); ADR-047
- **Downstream effects**: REQ-202 (propagation latency); catalog query evaluation for incoming changes (REQ-071)

## Risks of Non-Fulfillment

- Participants work on outdated views and repeat or contradict each other's changes (high)

## Trade-offs

- Recalculating catalogs and dashboards for every change costs server load; changes can be grouped into short batches (≤ 1 s)

## Implementation

- ADR(s): ADR-047
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Owner request and decisions of 2026-10-28 (UC-24): priority *should* after the MVP; presence and live changes in every view; real co-editing with automatic merge; sticky notes and comment threads in diagrams.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-28 | requirements-engineer | Initial draft (UC-24) |
