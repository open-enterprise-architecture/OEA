---
id: REQ-088
title: Go-live as an atomic DB transaction
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

# REQ-088: Go-live as an atomic DB transaction

## Statement

The go-live transition MUST be executed as an atomic DB transaction and comprise the following changes: (1) P1: `status→baseline`, `validFrom=now(UTC)`; (2) P0: `status→realized`, `validTo=now(UTC)`, read-only; (3) all entities in P1 with `lifecycle_state=retiring` → `lifecycle_state=retired`. In case of a technical error, a complete rollback MUST occur; no inconsistent state is permissible.

## Rationale

The go-live transition affects several tables. A partial commit would lead to an inconsistent architecture status — P1 would be baseline without P0 as realized, which permanently damages the architecture lifecycle.

## Acceptance Criteria

**AC1** (Rollback on DB error):

- When: a simulated DB error occurs after the P1 update
- Then: a complete rollback occurs; P0 and P1 are unchanged

**AC2** (Successful go-live):

- When: go-live is executed successfully
- Then: P1 has `status=baseline`, P0 has `status=realized`; all `retiring` entities in P1 have `lifecycle_state=retired`; all states are consistent

## Dependencies

- **Prerequisites**: REQ-086 (confirmation granted), REQ-084 (plateau exists)
- **Downstream effects**: REQ-089 (realized plateau is read-only)

## Implementation Notes

- Transaction isolation at least `REPEATABLE READ`
- Spring `@Transactional` with explicit rollback on all exceptions
- UTC timestamps for `validFrom`/`validTo` from system time at transaction start

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
