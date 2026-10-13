---
id: REQ-015
title: No parallel or repeated bootstrapping
type: business-rule
priority: must
status: proposed
version: 0.1.0
created: 2026-06-24
last_updated: 2026-06-24
author: requirements-engineer
references:
  use_cases:
    - UC-02
  business_objects:
    - system-admin-account
  business_rules:
    - BR-01
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-015: No parallel or repeated bootstrapping

## Statement

The system MUST NOT allow more than one active bootstrapping process at the same time, and MUST refuse a new bootstrapping attempt as soon as a [system admin account](../../business-objects/system-admin-account.md) already exists for the instance.

## Rationale

Without this rule, a race condition between two parallel bootstrapping attempts could lead to inconsistent states, or an attacker with short-term access to an already configured instance could try to create an additional system admin account of their own. UC-02, Business Rule BR-01 (`system-admin-account.md`) and Exception Flow E3.

## Context

Applies to the period of initial installation (race condition protection) as well as to any later point at which an account already exists (repetition protection).

## Type-Specific Fields

### For type=business-rule

**Trigger**: onCreate (attempt to create a system admin account)

**Affected Business Objects**: [system-admin-account](../../business-objects/system-admin-account.md)

**Formal Statement** (simplified):
```
if exists(SystemAdminAccount for instance) or bootstrappingInProgress(instance):
    reject(new bootstrapping attempt)
```

**Example**: Two parallel setup wizard calls on the same freshly installed instance must not lead to two different system admin accounts; only the first completed process wins, the second is rejected.

## Acceptance Criteria

**AC1**:
- Given: two bootstrapping attempts started almost simultaneously on the same fresh instance
- When: both compete to complete
- Then: exactly one completes successfully, the other is rejected

**AC2**:
- Given: an instance with an already existing system admin account
- When: a new bootstrapping attempt is made
- Then: it is rejected without an explicit reset action by an existing system admin account

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: parallel bootstrapping attempts (concurrency test), bootstrapping attempt on an already configured instance
- [x] Measurement tool: test suite of the auth module
- [x] Pass criterion: AC1, AC2 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-013 and/or REQ-014 (there must be a bootstrapping mechanism at all that this rule restricts)
- **Downstream effects**: none known
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without this rule, a second, unauthorized system admin account could arise alongside the legitimate one – severe security risk (privilege escalation vector)

## Trade-offs

- none known

## Implementation Notes

- Requires an atomic check-and-set mechanism (e.g., database constraint or distributed lock), not just a simple existence check before writing

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-02, Exception Flow E3, and Business Rule BR-01 from `system-admin-account.md`.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-02 |
