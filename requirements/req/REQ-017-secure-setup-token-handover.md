---
id: REQ-017
title: Secure handover of the setup token
type: constraint
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

# REQ-017: Secure handover of the setup token

## Statement

The initial setup token or the prompt interface for password assignment MUST NOT be delivered via a channel that is reachable without prior privileged access to the instance (e.g., server/container access).

## Rationale

Since no RBAC applies at bootstrapping time, the handover channel of the setup token is the only protection mechanism against unauthorized takeover of the first system admin access. UC-02, Implementation Notes.

## Context

Concerns REQ-013 (local bootstrapping): the way Max receives the setup token or the password prompt.

## Type-Specific Fields

### For type=constraint

**Type of constraint**: technical

**Source**: Security best practice (principle: initial bootstrap access only via channels that already require infrastructure access)

**Binding strength**: hard (cannot be bypassed)

**Impact of non-compliance**: A setup interface reachable over the open network without prior authentication would be a direct takeover vector for the entire instance

## Acceptance Criteria

**AC1**:
- Given: a freshly installed instance
- When: the setup token is generated
- Then: it is retrievable only via a channel that already requires privileged access (e.g., CLI output at startup, local log file, container exec)

**AC2**:
- Given: the initial setup web interface (if UI-based)
- When: it is called without a valid setup token
- Then: it refuses password assignment

## Verification Method

- [x] Method: test (automated) + inspection
- [x] Test setup: check that the setup token cannot be retrieved via unprotected network endpoints
- [x] Measurement tool: security test suite / manual inspection during implementation
- [x] Pass criterion: AC1, AC2 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-013
- **Downstream effects**: none known
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: An openly reachable setup interface allows any network participant to take over the instance before its actual operator – critical

## Trade-offs

- vs. usability: requiring CLI/server access slightly raises the entry barrier compared to a purely web-based setup, but is necessary from a security standpoint

## Implementation Notes

- The concrete channel (CLI stdout, temporary file with restrictive permissions, one-time setup URL with short validity) is solution design, not part of this requirement (see UC-02, open questions)

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-02, Implementation Notes and the open question on setup token handover.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-02 |
