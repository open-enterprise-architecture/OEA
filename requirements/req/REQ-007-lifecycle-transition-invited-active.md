---
id: REQ-007
title: Lifecycle transition invited→active on first successful login
type: business-rule
priority: should
status: proposed
version: 0.1.0
created: 2026-06-24
last_updated: 2026-06-24
author: requirements-engineer
references:
  use_cases:
    - UC-01
  business_objects:
    - person
  business_rules: []
  stakeholders:
    - SH-03
  concept:
    - concept/20-entities/08-organization-roles-persons.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-007: Lifecycle transition invited→active on first successful login

## Statement

The system SHOULD change the lifecycle status of a [Person](../../business-objects/person.md) from `invited` to `active` as soon as their first successful login process is completed.

## Rationale

UC-01, postcondition on success. The lifecycle of Person (see `person.md`) distinguishes `invited` (referenced in the system but not yet authenticated) from `active`. Login is the defined transition trigger.

## Context

Concerns persons who were newly created/referenced in the repository but have not yet logged in.

## Type-Specific Fields

### For type=business-rule

**Trigger**: onLogin (genauer: beim ersten erfolgreichen Login)

**Affected Business Objects**: [person](../../business-objects/person.md)

**Formal Statement** (vereinfacht):

```text
if Person.status == "invited" and Login.result == "success":
    Person.status = "active"
```

**Example**: A newly invited person logs in via the identity provider for the first time; their status changes from `invited` to `active`, regardless of whether a role has already been assigned to them (see REQ-003).

## Acceptance Criteria

**AC1**:

- Given: a person with status `invited`
- When: they log in successfully for the first time
- Then: their status changes to `active`

**AC2**:

- Given: a person with status `active`
- When: they log in again
- Then: their status remains `active` unchanged (no repeated transition, no side effects)

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: login simulation for an `invited` person and for an already `active` person
- [x] Measurement tool: test suite of the auth module
- [x] Pass criterion: AC1, AC2 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-001
- **Downstream effects**: none known
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without an automatic transition, the lifecycle status would have to be maintained manually – inconsistency risk, low severity

## Trade-offs

- none known

## Implementation Notes

- The transition should be idempotent (repeated login with an already `active` status must not throw an error)

## Implementation

- ADR(s): none
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-01 Login, postcondition on success, and the lifecycle definition in `person.md`.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-01 |
