---
id: REQ-088
title: Go-live as an atomic DB transaction
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

- Realigned by the OpenSpec change `us-086-plateau-go-live` (owner decisions 2026-11-17): the transition also takes the active solutions of the plateau live (US-157); `validFrom`/`validTo` are the day of the go-live (UTC); `lifecycle_state=retired` is the soft deletion of the entity by its delta `retiring`; only the plateau that succeeds the baseline goes live, and the plateaus that succeeded the old baseline succeed the new one

- Transaction isolation at least `REPEATABLE READ`
- Spring `@Transactional` with explicit rollback on all exceptions
- UTC timestamps for `validFrom`/`validTo` from system time at transaction start

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-17 | Change us-086-plateau-go-live | Implemented: `goLivePlateau` in one transaction (`PlateauGoLiveTests`) |
