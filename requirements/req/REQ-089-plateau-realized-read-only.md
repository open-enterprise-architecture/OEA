---
id: REQ-089
title: Realized plateau read-only
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
    - architecture
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-089: Realized plateau read-only

## Statement

Plateaus with `status=realized` MUST be locked for all write operations (read-only). Attempts to change the name, description, `validFrom` or linked solutions of a realized plateau MUST be rejected with HTTP 422.

## Rationale

Realized plateaus represent immutable architecture history. Subsequent changes would undermine the audit integrity of the repository and invalidate compliance evidence.

## Acceptance Criteria

**AC1** (API write protection):

- When: a PUT request is sent to `/plateaus/{id}` for a realized plateau
- Then: the API responds with HTTP 422 "Realized plateau is read-only"

**AC2** (UI write protection):

- When: a realized plateau is displayed in the overview
- Then: no edit button is visible

## Dependencies

- **Prerequisites**: REQ-088 (go-live transition sets plateau to realized)
- **Downstream effects**: none

## Implementation Notes

- Write protection does not apply to internal system updates (e.g., setting `validTo` by go-live)
- Implement guard logic in the service layer before every write endpoint
- Hide UI buttons via plateau status check when rendering the overview

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
