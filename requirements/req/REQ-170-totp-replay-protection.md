---
id: REQ-170
title: TOTP replay protection
type: business-rule
priority: should
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-03
  business_objects:
    - local-credential
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-170: TOTP Replay Protection

## Statement

The system **MUST** reject a TOTP code that has already been accepted for the same credential within its validity window.

## Rationale

An observed TOTP code (shoulder surfing, phishing proxy) can otherwise be replayed within its validity window of up to 90 seconds (±1 step).

## Context

Source: security review SEC-09 (proposal PR-14). Requires a stored last-used time step per TOTP credential (follow-up for business engineer: `local-credential.md`, data model).

## Type-Specific Fields

**Trigger**: onLogin (TOTP verification)

**Affected business objects**: local-credential (type TOTP)

**Formal statement**:

```text
accept(code, credential) only if timeStep(code) > credential.lastUsedTimeStep
on accept: credential.lastUsedTimeStep = timeStep(code)
```

**Example**: A code accepted at 10:00:05 is rejected when submitted again at 10:00:40.

## Acceptance Criteria

**AC1**:

- Given: a TOTP code that was just used for a successful login
- When: it is submitted again in a second login within the same 90-second window
- Then: the second login fails

**AC2**:

- Given: two TOTP credentials of the same person
- When: a code is accepted for credential A
- Then: the stored last-used time step of A is updated and B is unaffected

## Verification Method

- [ ] Method: test (automated, controlled clock)
- [ ] Pass criterion: AC1, AC2 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-010, REQ-022, REQ-030
- **Follow-on effects**: none
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: replay of observed codes – medium

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-09 (PR-14) |
