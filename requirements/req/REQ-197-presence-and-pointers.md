---
id: REQ-197
title: Presence and live pointers of other participants
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
    - person
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

# REQ-197: Presence and live pointers of other participants

## Statement

The system SHOULD show every participant who has the same diagram, document, entity form, catalog or dashboard open to all other participants of that object, with name and a stable colour. In diagrams and documents it SHOULD show the mouse pointer and the selection of every participant; in forms and catalogs the field or row a participant is editing.

## Rationale

In workshops and parallel work, architects need to know who else is working on the same content and where, so that they coordinate instead of working against each other (UC-24 steps 2 and 11).

## Context

Presence is transient: it is neither stored nor recorded in the audit trail. A participant sees only the presence of others on objects they may read themselves.

## Type-Specific Fields

### For type=functional

**Trigger**: A participant opens, moves the pointer in, selects in, focuses a field of or leaves an object

**Input**: pointer position in canvas or document coordinates, selection, focused field or row

**Output / Behavior**: participant list with name and colour; pointers with name label; selection and focus markers in the colour of the participant

**Business rules**: presence disappears at the latest 5 seconds after a participant leaves or loses the connection; presence data is not persisted

## Acceptance Criteria

**AC1** (Pointers in a diagram):

- Given: Kurt and Michael have the same diagram open
- When: Michael moves his mouse pointer
- Then: Kurt sees Michael's pointer with his name in Michael's colour at the same canvas position

**AC2** (Focus in a form):

- Given: Kurt and Franz have the same entity form open
- When: Franz places the cursor in the field Description
- Then: Kurt sees the field marked with Franz's name and colour

**AC3** (Leaving):

- Given: three participants have a diagram open
- When: one closes the diagram or loses the connection
- Then: the others no longer see that participant after at most 5 seconds

**AC4** (Not persisted):

- Given: a collaboration session took place
- When: the audit trail and the database are inspected
- Then: they contain no pointer positions, selections or focus events

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: two or more browser sessions of different persons against the same instance (Playwright with several browser contexts)
- [x] Measuring tool: Playwright, backend integration tests
- [x] Pass criterion: all acceptance criteria green
- [ ] Integrated in CI: planned with the implementing change

## Dependencies

- **Prerequisites**: ADR-047 (collaboration session); REQ-203 (authorization of sessions)
- **Downstream effects**: REQ-202 (latency of pointer updates)

## Risks of Non-Fulfillment

- Participants overwrite each other's work in workshops because they do not see where the others are working (medium)

## Trade-offs

- Showing pointers in forms and tables would be noisy; there the focused field or row is shown instead

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
