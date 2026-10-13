---
id: REQ-004
title: Rejection of session creation for unknown or offboarded persons
type: functional
priority: must
status: proposed
version: 0.3.0
created: 2026-06-24
last_updated: 2026-10-13
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

The system MUST refuse session creation if a valid identity token cannot be mapped to a [Person](../../business-objects/person.md) in the repository via the immutable identity binding (`iss`, `sub`) or if the mapped person has any lifecycle status other than `active` or `invited`.

## Rationale

A valid identity token alone is not sufficient for access – the person must be known as active in the EA repository. Prevents access by former employees whose identity provider account may not yet have been deprovisioned. UC-01, Exception Flow E2.

## Context

Person mastership usually lies with the HR system (concept §8.7); the `active`/`offboarded` status in the repository may precede or lag behind the identity provider status in time.

Security review SEC-06 and SEC-07: only `offboarded` was rejected, so persons in other non-active states (e.g. `inactive`) could still log in; matching on mutable claims such as e-mail allows account takeover. Identity binding and claim validation are specified in REQ-166; session revocation on a lifecycle change in REQ-164. `invited` remains allowed because the first successful login performs the transition `invited → active` (REQ-007).

## Type-Specific Fields

### For type=functional

**Inputs**:

- Valid identity token with the claims `iss` and `sub` (validated per REQ-166)

**Processing**:

- Match the pair (`iss`, `sub`) against the stored identity binding of the person; never match on e-mail or other mutable claims alone
- Check the lifecycle status of the person found (`active` or `invited` required)

**Outputs**:

- On a match with status `active` or `invited`: session is created (see REQ-001)
- On no match or any other status: rejection

**Error Cases**:

- No match → rejection, audit log entry (see REQ-005)
- Match with a status other than `active`/`invited` (e.g. `inactive`, `offboarded`) → rejection, audit log entry

## Acceptance Criteria

**AC1**:

- Given: a valid identity token that cannot be mapped to a person in the repository
- When: the login process is completed
- Then: no session is created

**AC2**:

- Given: a valid identity token whose mapped person has the status `offboarded`
- When: the login process is completed
- Then: no session is created

**AC3**:

- Given: a valid identity token whose mapped person has the status `inactive` (or any other status except `active` and `invited`)
- When: the login process is completed
- Then: no session is created

**AC4**:

- Given: a valid identity token whose `email` claim matches a person but whose (`iss`, `sub`) pair differs from that person's stored binding
- When: the login process is completed
- Then: no session is created for that person

**AC5** (Reactivation configurable, owner decision Q19):

- Given: a person in status `offboarded` and the setting `allowReactivationOfOffboarded` disabled (default)
- When: a principal with `person:manage` tries to reactivate the person
- Then: the request is rejected; with the setting enabled the person returns to `invited` without role assignments and without local credentials, and the login is still rejected until the invitation is completed

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: login attempt with a token without person mapping or with an `offboarded` person
- [x] Measurement tool: test suite of the auth module
- [x] Pass criterion: AC1–AC4 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-001, REQ-166 (identity binding and token validation)
- **Downstream effects**: REQ-005 (Audit-Log), REQ-164 (session revocation on lifecycle change)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Offboarded persons could continue to have access to the EA repository in case of delayed IdP deprovisioning – severe, especially with sensitive architecture/compliance data

## Trade-offs

- vs. REQ-001 (speed of the login flow): the additional lookup step against the Person object slightly increases login latency

## Implementation Notes

- The lookup should be indexed via the identity binding (`iss`, `sub`) to keep login latency low (see performance note in UC-01)
- The person lifecycle status (not only a boolean `active`) and the identity binding must exist in the data model (follow-up for the business engineer)

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
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-06/SEC-07: reject every status other than `active`/`invited`; match on (`iss`, `sub`) only; AC3, AC4 added |
| 0.3.0 | 2026-10-13 | Lukas Mathis | AC5: reactivation of offboarded persons only if `allowReactivationOfOffboarded` is enabled (owner decision Q19) |
