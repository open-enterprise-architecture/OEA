---
id: REQ-006
title: No disclosure of account existence on failed login
type: constraint
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
    - concept/70-platform/21-api-architecture.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-006: No disclosure of account existence on failed login

## Statement

The system MUST NOT reveal, through different error messages or response times, whether a requested identity (email address, subject ID) exists in the repository or not.

## Rationale

Prevents user enumeration attacks in which an attacker finds out through systematic trial which identities are known in the system. UC-01, postcondition on failure and acceptance criteria.

## Context

Concerns in particular Exception Flows E1 (identity provider rejects) and E2 (person not found/offboarded) – both must appear identical to the outside.

## Type-Specific Fields

### For type=constraint

**Type of constraint**: technical

**Source**: Security best practice against user enumeration (OWASP); no external standard strictly prescribed, but an established standard

**Binding strength**: hard (cannot be bypassed)

**Impact of non-compliance**: Attackers can identify valid identities in the system and attack them in a targeted way (e.g., phishing, credential stuffing)

## Acceptance Criteria

**AC1**:

- Given: a login attempt with a non-existent identity
- When: the login fails
- Then: the externally visible error message is identical to that for an existing but incorrectly authenticated identity

**AC2**:

- Given: two failed login attempts, one with an existing and one with a non-existent identity
- When: the response times are compared
- Then: they do not differ in a way that an attacker can exploit (no timing side channel)

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: compare error message content and response time distribution for existing vs. non-existent identities
- [x] Measurement tool: test suite of the auth module, possibly a timing analysis script
- [x] Pass criterion: AC1 exact, AC2 within tolerated variance (threshold to be set during implementation)
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-001, REQ-004
- **Downstream effects**: none known
- **Conflicts**: REQ-005 (the internal audit log may/should be more detailed than the externally visible message)

## Risks of Non-Fulfillment

- Risk 1: User enumeration enables targeted attacks on known employees (phishing, credential stuffing) – severe, especially relevant for a tool with sensitive architecture/compliance data

## Trade-offs

- vs. usability: generic error messages are less helpful for legitimate users when debugging their own login problems

## Implementation Notes

- A constant response time (e.g., through artificial delay) may be necessary to avoid timing side channels

## Implementation

- ADR(s): none
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-01 Login, acceptance criteria, and Exception Flow E1/E2.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-01 |
