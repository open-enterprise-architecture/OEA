---
id: REQ-084
title: Create plateau
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
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
- `succeeds` reference is optional and only meaningfully set for `target` plateaus
- Secure the unique constraint on `status=baseline` per instance in the DB

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
