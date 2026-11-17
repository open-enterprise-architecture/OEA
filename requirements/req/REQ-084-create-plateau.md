---
id: REQ-084
title: Create plateau
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-28
last_updated: 2026-11-17
author: requirements-engineer
references:
  use_cases:
    - UC-11
  business_objects:
    - plateau
    - entity
    - solution
    - architecture
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-084: Create plateau

## Statement

The system MUST enable the creation of plateaus with the statuses `baseline`, `target` and `transition`. Mandatory field: `name`; optional: `description` (max. 2000 characters), `validFrom`, `succeeds` (reference to an existing plateau). At most one plateau per instance may have the status `baseline`.

## Rationale

Without plateau creation, no strategic architecture planning can take place in plateau mode. The plateau is the central unit for structuring current and target states in the EA repository.

## Acceptance Criteria

**AC1** (Create initial baseline):

- When: a plateau is created without a `succeeds` reference and with `status=baseline`
- Then: the API responds with HTTP 201 and `status=baseline`

**AC2** (Create target):

- When: a plateau is created with `status=target` and `succeeds`=baseline ID
- Then: the API responds with HTTP 201; `status=target` and `succeeds` are set correctly

**AC3** (Reject second baseline):

- When: a further plateau with `status=baseline` is created although a baseline plateau already exists
- Then: the API responds with HTTP 422 ("A baseline plateau already exists")

## Dependencies

- **Prerequisites**: UC-11 active (plateau mode enabled for the instance)
- **Downstream effects**: REQ-085 (overview), REQ-086 (go-live confirmation)

## Implementation Notes

- Plateau status is immutable after go-live (`realized`); no further writing allowed
- Secure the unique constraint on `status=baseline` per instance in the DB
- Realigned by the OpenSpec change `us-084-plateaus` (owner decisions 2026-11-17):
  - creating and editing plateaus needs the new permission `plateau:write` (role `enterprise-architect`); reading needs `plateau:write`, `plateau:go-live` or `solution:write`;
  - the first baseline turns plateau mode on; a target or transition plateau without a baseline gives 422 "No baseline plateau exists. Create an initial baseline first.";
  - a target or transition plateau needs `succeeds` (a plateau that is not realized, no cycle) and a `basis` (`catalog` or `predecessor`, US-155); a baseline has neither;
  - the status is set at creation and changed only by the plateau go-live; the predecessor does not change while a solution targets the plateau (409 `plateau-in-use`)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-17 | Change us-084-plateaus | Realigned: permission, predecessor and basis required for target and transition, plateau mode implicit |
