---
id: REQ-004
title: Rejection of session creation for unknown or offboarded persons
type: functional
priority: must
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

# REQ-004: Rejection of session creation for unknown or offboarded persons

## Statement

The system MUST refuse session creation if a valid identity token cannot be mapped to an active [Person](../../business-objects/person.md) in the repository or if the mapped person has the lifecycle status `offboarded`.

## Rationale

A valid identity token alone is not sufficient for access – the person must be known as active in the EA repository. Prevents access by former employees whose identity provider account may not yet have been deprovisioned. UC-01, Exception Flow E2.

## Context

Person mastership usually lies with the HR system (concept §8.7); the `active`/`offboarded` status in the repository may precede or lag behind the identity provider status in time.

## Type-Specific Fields

### For type=functional

**Inputs**:
- Valid identity token with identity claims (e.g., email, subject ID)

**Processing**:
- Match the claims against `Person.externalReference`
- Check the lifecycle status of the person found (`active` required)

**Outputs**:
- On a match with `active` status: session is created (see REQ-001)
- On no match or status `offboarded`: rejection

**Error Cases**:
- No match → rejection, audit log entry (see REQ-005)
- Match with status `offboarded` → rejection, audit log entry

## Acceptance Criteria

**AC1**:
- Given: a valid identity token that cannot be mapped to a person in the repository
- When: the login process is completed
- Then: no session is created

**AC2**:
- Given: a valid identity token whose mapped person has the status `offboarded`
- When: the login process is completed
- Then: no session is created

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: login attempt with a token without person mapping or with an `offboarded` person
- [x] Measurement tool: test suite of the auth module
- [x] Pass criterion: AC1, AC2 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-001
- **Downstream effects**: REQ-005 (Audit-Log)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Offboarded persons could continue to have access to the EA repository in case of delayed IdP deprovisioning – severe, especially with sensitive architecture/compliance data

## Trade-offs

- vs. REQ-001 (speed of the login flow): the additional lookup step against the Person object slightly increases login latency

## Implementation Notes

- The lookup should be indexed via `externalReference` to keep login latency low (see performance note in UC-01)

## Implementation

- ADR(s): none
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-01 Login, Exception Flow E2.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-01 |
